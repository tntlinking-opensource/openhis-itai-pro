package com.openhis.whale.einvoice.boss.application.query;

import com.openhis.whale.einvoice.boss.application.compensation.BossCompensationClassifier;
import com.openhis.whale.einvoice.boss.application.config.EinvoiceBossProperties;
import com.openhis.whale.einvoice.boss.application.dto.BossDocumentListItemDto;
import com.openhis.whale.einvoice.boss.application.dto.BossPendingCompensationStatsDto;
import com.openhis.whale.einvoice.boss.application.dto.ListPendingCompensationResponse;
import com.openhis.whale.einvoice.boss.application.dto.PageDocumentsResponse;
import com.openhis.whale.einvoice.boss.application.account.BossAccountQueryService;
import com.openhis.whale.einvoice.boss.application.dto.AccountStatusResponse;
import com.openhis.whale.einvoice.boss.application.request.AccountStatusRequest;
import com.openhis.whale.einvoice.boss.application.request.ListPendingCompensationRequest;
import com.openhis.whale.einvoice.boss.application.request.PageDocumentsRequest;
import com.openhis.whale.einvoice.boss.contracts.dto.BossCompensationRowDto;
import com.openhis.whale.einvoice.boss.contracts.dto.BossIssueSnapshot;
import com.openhis.whale.einvoice.boss.contracts.dto.BossIssueStatusDto;
import com.openhis.whale.einvoice.boss.contracts.enums.CompensationExceptionType;
import com.openhis.whale.einvoice.boss.contracts.enums.IssueKind;
import com.openhis.whale.einvoice.boss.contracts.enums.TaskStatus;
import com.openhis.whale.einvoice.boss.contracts.reader.HealthInvoiceSnapshotReader;
import com.openhis.whale.einvoice.boss.domain.document.entity.EinvBossDocument;
import com.openhis.whale.einvoice.boss.domain.document.repository.EinvBossDocumentRepository;
import com.openhis.whale.einvoice.boss.domain.task.entity.EinvBossIssueTask;
import com.openhis.whale.einvoice.boss.domain.task.repository.EinvBossIssueTaskRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * 博思电子票据查询编排（待补偿 P0 + 进度查询）。
 */
@Service
@RequiredArgsConstructor
public class EinvoiceBossQueryAppService {

    private static final int SCAN_LIMIT = 500;
    private static final int PAGE_INVOICE_FILTER_LIMIT = 1000;

    private final HealthInvoiceSnapshotReader healthInvoiceSnapshotReader;
    private final EinvBossDocumentRepository documentRepository;
    private final EinvBossIssueTaskRepository taskRepository;
    private final EinvoiceBossProperties properties;
    private final BossAccountQueryService accountQueryService;

    /**
     * 按支付主键查询开票进度。
     *
     * @param paymentId 支付主键
     * @return 开票进度
     */
    public BossIssueStatusDto getIssueStatus(Long paymentId) {
        BossIssueStatusDto dto = new BossIssueStatusDto();
        Optional<EinvBossDocument> document = documentRepository.findByPaymentIdAndIsDeletedFalse(paymentId);
        if (document.isEmpty()) {
            return dto;
        }
        EinvBossDocument doc = document.get();
        BossIssueSnapshot snapshot = healthInvoiceSnapshotReader.loadForInvoice(doc.getInvoiceId());
        dto.setInvoiceStatus(snapshot.getInvoiceStatus());
        dto.setAmountStatus(doc.getAmountStatus());
        dto.setBillNo(doc.getBossBillNo());
        dto.setQrReady(doc.getBillQrCode() != null);
        taskRepository.findByInvoiceIdAndIsDeletedFalse(doc.getInvoiceId()).ifPresent(task -> {
            dto.setTaskStatus(task.getStatus());
        });
        return dto;
    }

    /**
     * 待补偿统计（角标/顶栏）。
     *
     * @return 统计
     */
    public BossPendingCompensationStatsDto getPendingCompensationStats() {
        List<BossCompensationRowDto> rows = loadCompensationRows(null);
        return toStats(rows);
    }

    /**
     * 待补偿数量（兼容旧接口）。
     *
     * @return 数量
     */
    public long getPendingCount() {
        return getPendingCompensationStats().getPendingCount();
    }

    /**
     * 待补偿列表（含统计摘要）。
     *
     * @param request 查询条件
     * @return 分页结果
     */
    /**
     * 电子发票分页查询（P1）。
     *
     * @param request 筛选与分页
     * @return 分页结果
     */
    public PageDocumentsResponse pageDocuments(PageDocumentsRequest request) {
        PageDocumentsRequest safe = request == null ? new PageDocumentsRequest() : request;
        int pageNo = safe.getPageNo() == null || safe.getPageNo() < 1 ? 1 : safe.getPageNo();
        int pageSize = safe.getPageSize() == null || safe.getPageSize() < 1 ? 20 : safe.getPageSize();

        Specification<EinvBossDocument> spec = buildDocumentPageSpec(safe);
        Page<EinvBossDocument> page = documentRepository.findAll(
                spec,
                PageRequest.of(pageNo - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt")));

        PageDocumentsResponse response = new PageDocumentsResponse();
        response.setTotal(page.getTotalElements());
        List<BossDocumentListItemDto> rows = new ArrayList<>();
        for (EinvBossDocument document : page.getContent()) {
            try {
                rows.add(toListItem(document));
            } catch (Exception ignored) {
                // 单条快照失败不阻断列表
            }
        }
        response.setRows(rows);
        return response;
    }

    /**
     * 查询博思入账状态（P3）。
     *
     * @param request 票面或单据主键
     * @return 入账状态
     */
    public AccountStatusResponse getAccountStatus(AccountStatusRequest request) {
        if (request == null) {
            throw new com.openhis.whale.core.application.exception.BusinessException(
                    com.openhis.whale.einvoice.boss.contracts.constants.EinvBossErrorCodes.EINV_DOCUMENT_NOT_FOUND,
                    "请求不能为空");
        }
        String billBatchCode = request.getBillBatchCode();
        String billNo = request.getBillNo();
        if (request.getDocumentId() != null) {
            EinvBossDocument document = documentRepository.findById(request.getDocumentId())
                    .filter(doc -> !Boolean.TRUE.equals(doc.getIsDeleted()))
                    .orElseThrow(() -> new com.openhis.whale.core.application.exception.BusinessException(
                            com.openhis.whale.einvoice.boss.contracts.constants.EinvBossErrorCodes.EINV_DOCUMENT_NOT_FOUND,
                            "单据不存在"));
            billBatchCode = document.getBossBatchCode();
            billNo = document.getBossBillNo();
        }
        if (!StringUtils.hasText(billBatchCode) || !StringUtils.hasText(billNo)) {
            throw new com.openhis.whale.core.application.exception.BusinessException(
                    com.openhis.whale.einvoice.boss.contracts.constants.EinvBossErrorCodes.EINV_DOCUMENT_NOT_FOUND,
                    "请先开具电子票后再查询入账状态");
        }
        BossAccountQueryService.BossAccountStatus boss = accountQueryService.query(billBatchCode, billNo);
        AccountStatusResponse response = new AccountStatusResponse();
        response.setBillBatchCode(billBatchCode);
        response.setBillNo(billNo);
        response.setBossSuccess(boss.isSuccess());
        response.setBossResultCode(boss.getResultCode());
        response.setBossMessage(boss.getRawMessage());
        response.setAccountStatus(boss.getAccountStatus());
        response.setAccountStatusLabel(resolveAccountStatusLabel(boss.getAccountStatus()));
        response.setAccountDate(boss.getAccountDate());
        return response;
    }

    public ListPendingCompensationResponse listPendingCompensation(ListPendingCompensationRequest request) {
        ListPendingCompensationRequest safe = request == null ? new ListPendingCompensationRequest() : request;
        List<BossCompensationRowDto> filtered = loadCompensationRows(safe);
        ListPendingCompensationResponse response = new ListPendingCompensationResponse();
        response.setStats(toStats(filtered));

        int pageNo = safe.getPageNo() == null || safe.getPageNo() < 1 ? 1 : safe.getPageNo();
        int pageSize = safe.getPageSize() == null || safe.getPageSize() < 1 ? 20 : safe.getPageSize();
        int from = (pageNo - 1) * pageSize;
        response.setTotal(filtered.size());
        if (from >= filtered.size()) {
            response.setRows(List.of());
            return response;
        }
        int to = Math.min(from + pageSize, filtered.size());
        response.setRows(filtered.subList(from, to));
        return response;
    }

    private Specification<EinvBossDocument> buildDocumentPageSpec(PageDocumentsRequest filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.or(cb.isFalse(root.get("isDeleted")), cb.isNull(root.get("isDeleted"))));

            if (StringUtils.hasText(filter.getIssueKind())) {
                predicates.add(cb.equal(root.get("issueKind"), filter.getIssueKind().trim()));
            }
            if (StringUtils.hasText(filter.getBillNo())) {
                predicates.add(cb.like(root.get("bossBillNo"), "%" + filter.getBillNo().trim() + "%"));
            }
            if (filter.getDateFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.getDateFrom().atStartOfDay()));
            }
            if (filter.getDateTo() != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), filter.getDateTo().plusDays(1).atStartOfDay()));
            }
            if (StringUtils.hasText(filter.getKeyword()) || StringUtils.hasText(filter.getInvoiceStatus())) {
                List<Long> invoiceIds = healthInvoiceSnapshotReader.findInvoiceIdsByKeyword(
                        filter.getKeyword(), filter.getInvoiceStatus(), PAGE_INVOICE_FILTER_LIMIT);
                if (invoiceIds.isEmpty()) {
                    predicates.add(cb.disjunction());
                } else {
                    predicates.add(root.get("invoiceId").in(invoiceIds));
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private BossDocumentListItemDto toListItem(EinvBossDocument document) {
        BossDocumentListItemDto item = new BossDocumentListItemDto();
        item.setDocumentId(document.getId());
        item.setInvoiceId(document.getInvoiceId());
        item.setPaymentId(document.getPaymentId());
        item.setIssueKind(document.getIssueKind());
        item.setIssueKindLabel(resolveIssueKindLabel(document.getIssueKind()));
        item.setPaymentAmt(document.getPaymentAmt());
        item.setBossBillNo(document.getBossBillNo());
        item.setAmountStatus(document.getAmountStatus());
        item.setBusNo(document.getBusNo());
        item.setCreatedAt(document.getCreatedAt());
        if (IssueKind.COM.getCode().equals(document.getIssueKind())) {
            item.setPatientName(document.getPayerName());
            item.setInvoiceStatus(StringUtils.hasText(document.getBossBillNo()) ? "ISSUED" : "DRAFT");
            return item;
        }
        BossIssueSnapshot snapshot = healthInvoiceSnapshotReader.loadForInvoice(document.getInvoiceId());
        item.setPatientName(snapshot.getPatientName());
        item.setInvoiceStatus(snapshot.getInvoiceStatus());
        item.setIssuedAt(snapshot.getIssuedAt());
        return item;
    }

    private List<BossCompensationRowDto> loadCompensationRows(ListPendingCompensationRequest filter) {
        LocalDateTime now = LocalDateTime.now();
        int staleMinutes = properties.getCompensation().getStaleMinutes();
        Set<Long> invoiceIds = collectCandidateInvoiceIds(now.minusMinutes(staleMinutes));
        List<BossCompensationRowDto> rows = new ArrayList<>();
        for (Long invoiceId : invoiceIds) {
            try {
                BossIssueSnapshot snapshot = healthInvoiceSnapshotReader.loadForInvoice(invoiceId);
                Optional<EinvBossIssueTask> task = taskRepository.findByInvoiceIdAndIsDeletedFalse(invoiceId);
                Optional<CompensationExceptionType> type = BossCompensationClassifier.classify(
                        snapshot, task, staleMinutes, now);
                if (type.isEmpty()) {
                    continue;
                }
                BossCompensationRowDto row = toRow(snapshot, task, documentRepository.findByInvoiceIdAndIsDeletedFalse(invoiceId), type.get(), now);
                if (!matchesFilter(row, filter)) {
                    continue;
                }
                rows.add(row);
            } catch (Exception ignored) {
                // 单条快照失败不阻断列表
            }
        }
        rows.sort(Comparator.comparingLong(BossCompensationRowDto::getWaitingMinutes).reversed());
        return rows;
    }

    private Set<Long> collectCandidateInvoiceIds(LocalDateTime staleThreshold) {
        Set<Long> invoiceIds = new LinkedHashSet<>();
        invoiceIds.addAll(healthInvoiceSnapshotReader.findOrphanDraftInvoiceIds(SCAN_LIMIT));
        invoiceIds.addAll(healthInvoiceSnapshotReader.findDraftInvoiceIdsBefore(staleThreshold, SCAN_LIMIT));
        for (EinvBossIssueTask task : taskRepository.findByStatusAndIsDeletedFalse(TaskStatus.FAILED.getCode())) {
            invoiceIds.add(task.getInvoiceId());
        }
        for (EinvBossIssueTask task : taskRepository.findByStatusAndIsDeletedFalse(TaskStatus.PENDING.getCode())) {
            invoiceIds.add(task.getInvoiceId());
        }
        return invoiceIds;
    }

    private boolean matchesFilter(BossCompensationRowDto row, ListPendingCompensationRequest filter) {
        if (filter == null) {
            return true;
        }
        if (StringUtils.hasText(filter.getExceptionType())
                && !filter.getExceptionType().equalsIgnoreCase(row.getExceptionType())) {
            return false;
        }
        if (StringUtils.hasText(filter.getIssueKind())
                && !filter.getIssueKind().equalsIgnoreCase(row.getIssueKind())) {
            return false;
        }
        if (StringUtils.hasText(filter.getKeyword())) {
            String keyword = filter.getKeyword().trim().toLowerCase(Locale.ROOT);
            String patient = row.getPatientName() == null ? "" : row.getPatientName().toLowerCase(Locale.ROOT);
            String encounter = row.getEncounterBusNo() == null ? "" : row.getEncounterBusNo().toLowerCase(Locale.ROOT);
            if (!patient.contains(keyword) && !encounter.contains(keyword)) {
                return false;
            }
        }
        if (filter.getDateFrom() != null || filter.getDateTo() != null) {
            // 列表无独立业务日期字段，按等待时长反推不可靠；此处仅当 keyword/类型过滤已满足时保留全部
            // P1 可在 row 增加 billDate 再精确过滤
        }
        return true;
    }

    private BossCompensationRowDto toRow(
            BossIssueSnapshot snapshot,
            Optional<EinvBossIssueTask> task,
            Optional<EinvBossDocument> document,
            CompensationExceptionType type,
            LocalDateTime now) {
        BossCompensationRowDto row = new BossCompensationRowDto();
        row.setInvoiceId(snapshot.getInvoiceId());
        row.setPaymentId(snapshot.getPaymentId());
        document.ifPresent(doc -> row.setDocumentId(doc.getId()));
        row.setPatientName(snapshot.getPatientName());
        row.setEncounterBusNo(snapshot.getEncounterBusNo());
        row.setIssueKind(resolveIssueKind(snapshot));
        row.setIssueKindLabel(resolveIssueKindLabel(row.getIssueKind()));
        row.setPaymentAmt(snapshot.getPaymentAmt());
        row.setBusNo(snapshot.getBusNo());
        row.setExceptionType(type.getCode());
        row.setExceptionTypeLabel(resolveExceptionLabel(type));
        row.setWaitingMinutes(BossCompensationClassifier.waitingMinutes(snapshot, now));
        task.ifPresent(active -> {
            row.setTaskStatus(active.getStatus());
            row.setLastErrorCode(active.getLastErrorCode());
            row.setLastErrorMsg(active.getLastErrorMsg());
        });
        fillActions(row, type);
        return row;
    }

    private void fillActions(BossCompensationRowDto row, CompensationExceptionType type) {
        switch (type) {
            case LOST -> {
                row.setCanRequeue(true);
                row.setCanRecoverByBusNo(true);
            }
            case AMOUNT -> {
                row.setCanAdjustAmount(true);
                row.setCanIssueNow(true);
                row.setCanRequeue(true);
            }
            case FAILED -> {
                row.setCanIssueNow(true);
                row.setCanRequeue(true);
                row.setCanRecoverByBusNo(true);
            }
            case STALE -> {
                row.setCanRecoverByBusNo(true);
                row.setCanRequeue(true);
            }
            default -> {
            }
        }
    }

    private BossPendingCompensationStatsDto toStats(List<BossCompensationRowDto> rows) {
        BossPendingCompensationStatsDto stats = new BossPendingCompensationStatsDto();
        stats.setPendingCount(rows.size());
        for (BossCompensationRowDto row : rows) {
            switch (CompensationExceptionType.valueOf(row.getExceptionType())) {
                case LOST -> stats.setLostCount(stats.getLostCount() + 1);
                case AMOUNT -> stats.setAmountCount(stats.getAmountCount() + 1);
                case FAILED -> stats.setFailedCount(stats.getFailedCount() + 1);
                case STALE -> stats.setStaleCount(stats.getStaleCount() + 1);
                default -> {
                }
            }
        }
        return stats;
    }

    private String resolveIssueKind(BossIssueSnapshot snapshot) {
        String typeCode = snapshot.getInvoiceTypeCode();
        if (typeCode != null && typeCode.toUpperCase(Locale.ROOT).contains("INPATIENT")) {
            return IssueKind.IMP.getCode();
        }
        if (typeCode != null && typeCode.toUpperCase(Locale.ROOT).contains("REG")) {
            return IssueKind.REG.getCode();
        }
        return IssueKind.AMB.getCode();
    }

    private String resolveIssueKindLabel(String issueKind) {
        if (IssueKind.REG.getCode().equals(issueKind)) {
            return "挂号";
        }
        if (IssueKind.IMP.getCode().equals(issueKind)) {
            return "住院";
        }
        if (IssueKind.COM.getCode().equals(issueKind)) {
            return "通用票";
        }
        return "门诊";
    }

    private String resolveAccountStatusLabel(String status) {
        if (!StringUtils.hasText(status)) {
            return "未知";
        }
        return switch (status) {
            case "0", "00" -> "未入账";
            case "1", "01" -> "已入账";
            case "2", "02" -> "入账失败";
            default -> status;
        };
    }

    private String resolveExceptionLabel(CompensationExceptionType type) {
        return switch (type) {
            case LOST -> "事件丢失";
            case AMOUNT -> "金额待调整";
            case FAILED -> "开票失败";
            case STALE -> "超时待查";
        };
    }
}
