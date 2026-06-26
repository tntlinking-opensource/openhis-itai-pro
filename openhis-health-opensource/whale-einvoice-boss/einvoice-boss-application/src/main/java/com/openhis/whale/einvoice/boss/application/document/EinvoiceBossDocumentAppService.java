package com.openhis.whale.einvoice.boss.application.document;

import com.alibaba.fastjson2.JSON;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.session.CurrentOperatorResolver;
import com.openhis.whale.einvoice.boss.application.dto.BossAdjustLogDto;
import com.openhis.whale.einvoice.boss.application.dto.BossAmountValidateResultDto;
import com.openhis.whale.einvoice.boss.application.dto.BossDocumentBriefDto;
import com.openhis.whale.einvoice.boss.application.dto.BossDocumentDetailDto;
import com.openhis.whale.einvoice.boss.application.dto.BossTaskSummaryDto;
import com.openhis.whale.einvoice.boss.application.request.SaveDocumentAdjustmentRequest;
import com.openhis.whale.einvoice.boss.contracts.constants.EinvBossErrorCodes;
import com.openhis.whale.einvoice.boss.contracts.dto.BossIssueSnapshot;
import com.openhis.whale.einvoice.boss.contracts.dto.BossLineDto;
import com.openhis.whale.einvoice.boss.contracts.enums.AmountStatus;
import com.openhis.whale.einvoice.boss.contracts.enums.IssueKind;
import com.openhis.whale.einvoice.boss.contracts.reader.HealthInvoiceSnapshotReader;
import com.openhis.whale.einvoice.boss.domain.document.entity.EinvBossAmountAdjustLog;
import com.openhis.whale.einvoice.boss.domain.document.entity.EinvBossDocument;
import com.openhis.whale.einvoice.boss.domain.document.entity.EinvBossLine;
import com.openhis.whale.einvoice.boss.domain.document.repository.EinvBossAmountAdjustLogRepository;
import com.openhis.whale.einvoice.boss.domain.document.repository.EinvBossDocumentRepository;
import com.openhis.whale.einvoice.boss.domain.document.repository.EinvBossLineRepository;
import com.openhis.whale.einvoice.boss.domain.service.BossInvoiceAmountValidator;
import com.openhis.whale.einvoice.boss.domain.task.entity.EinvBossIssueTask;
import com.openhis.whale.einvoice.boss.domain.task.repository.EinvBossIssueTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 博思电子票据单据编排（金额调整 P1）。
 */
@Service
@RequiredArgsConstructor
public class EinvoiceBossDocumentAppService {

    private static final String BALANCE_CHARGE_CODE = "BALANCE_ADJ";

    private final EinvBossDocumentRepository documentRepository;
    private final EinvBossLineRepository lineRepository;
    private final EinvBossAmountAdjustLogRepository adjustLogRepository;
    private final EinvBossIssueTaskRepository taskRepository;
    private final HealthInvoiceSnapshotReader healthInvoiceSnapshotReader;
    private final BossInvoiceAmountValidator amountValidator;
    /** 写路径操作人门控（B.6.5），禁止 AppService 直读 ICurrentUser。 */
    private final CurrentOperatorResolver currentOperatorResolver;

    /**
     * 按支付主键查询单据摘要（账单 Banner 跳转）。
     *
     * @param paymentId 支付主键
     * @return 摘要（无单据时字段为空）
     */
    public BossDocumentBriefDto getBriefByPaymentId(Long paymentId) {
        BossDocumentBriefDto dto = new BossDocumentBriefDto();
        if (paymentId == null) {
            return dto;
        }
        documentRepository.findByPaymentIdAndIsDeletedFalse(paymentId).ifPresent(document -> {
            dto.setDocumentId(document.getId());
            dto.setInvoiceId(document.getInvoiceId());
        });
        return dto;
    }

    /**
     * 按单据主键查询详情。
     *
     * @param documentId 单据主键
     * @return 详情
     */
    public BossDocumentDetailDto getByDocumentId(Long documentId) {
        EinvBossDocument document = requireDocument(documentId);
        return buildDetail(document);
    }

    /**
     * 按票据主键查询详情。
     *
     * @param invoiceId 票据主键
     * @return 详情
     */
    public BossDocumentDetailDto getByInvoiceId(Long invoiceId) {
        EinvBossDocument document = documentRepository.findByInvoiceIdAndIsDeletedFalse(invoiceId)
                .orElseThrow(() -> new BusinessException(EinvBossErrorCodes.EINV_DOCUMENT_NOT_FOUND, "无电子票据单据"));
        return buildDetail(document);
    }

    /**
     * 预校验金额。
     *
     * @param documentId 单据主键
     * @return 校验结果
     */
    public BossAmountValidateResultDto validateAmount(Long documentId) {
        EinvBossDocument document = requireDocument(documentId);
        List<EinvBossLine> lines = lineRepository.findByDocumentIdAndIsDeletedFalseOrderByLineNoAsc(document.getId());
        return computeValidateResult(document.getTotalAmt(), lines);
    }

    /**
     * 保存金额调整。
     *
     * @param request 调整请求
     * @return 更新后详情
     */
    public BossDocumentDetailDto saveAdjustment(SaveDocumentAdjustmentRequest request) {
        if (request == null || request.getDocumentId() == null) {
            throw new BusinessException(EinvBossErrorCodes.EINV_DOCUMENT_NOT_FOUND, "单据主键不能为空");
        }
        String reason = request.getReason() == null ? "" : request.getReason().trim();
        if (reason.length() < 5) {
            throw new BusinessException(EinvBossErrorCodes.EINV_AMOUNT_MISMATCH, "调整原因至少 5 个字");
        }
        EinvBossDocument document = requireDocument(request.getDocumentId());
        BigDecimal beforeTotal = document.getTotalAmt();
        BigDecimal targetTotal = request.getTotalAmt() == null ? beforeTotal : request.getTotalAmt();

        List<EinvBossLine> existingLines = lineRepository.findByDocumentIdAndIsDeletedFalseOrderByLineNoAsc(document.getId());
        Map<Long, EinvBossLine> lineMap = existingLines.stream()
                .collect(Collectors.toMap(EinvBossLine::getId, line -> line));

        List<EinvBossLine> workingLines = new ArrayList<>();
        int maxLineNo = existingLines.stream().map(EinvBossLine::getLineNo).max(Integer::compareTo).orElse(0);
        for (SaveDocumentAdjustmentRequest.LineItem item : request.getLines()) {
            if (item.getLineId() != null && lineMap.containsKey(item.getLineId())) {
                EinvBossLine line = lineMap.get(item.getLineId());
                line.setAmount(scale2(item.getAmount()));
                line.setAdjusted(true);
                line.setAdjustReason(reason);
                workingLines.add(line);
            } else if (item.isNewLine() || item.getLineId() == null) {
                EinvBossLine line = new EinvBossLine();
                line.setTenantId(document.getTenantId());
                line.setDocumentId(document.getId());
                line.setLineType("CHARGE");
                line.setLineNo(++maxLineNo);
                line.setChargeCode(StringUtils.hasText(item.getChargeCode()) ? item.getChargeCode() : BALANCE_CHARGE_CODE);
                line.setChargeName(StringUtils.hasText(item.getChargeName()) ? item.getChargeName() : "金额调平");
                line.setStdAmount(scale2(item.getStdAmount() != null ? item.getStdAmount() : item.getAmount()));
                line.setAmount(scale2(item.getAmount()));
                line.setQuantity(item.getQuantity() != null ? item.getQuantity() : BigDecimal.ONE);
                line.setUnit(item.getUnit() != null ? item.getUnit() : "次");
                line.setAdjusted(true);
                line.setAdjustReason(reason);
                line.setIsDeleted(false);
                workingLines.add(line);
            }
        }
        if (workingLines.isEmpty()) {
            workingLines.addAll(existingLines);
        }

        List<BigDecimal> amounts = workingLines.stream().map(EinvBossLine::getAmount).collect(Collectors.toList());
        amountValidator.validate(scale2(targetTotal), amounts);

        for (EinvBossLine line : workingLines) {
            lineRepository.save(line);
        }
        document.setTotalAmt(scale2(targetTotal));
        document.setAmountStatus(AmountStatus.ADJUSTED.getCode());
        documentRepository.save(document);

        EinvBossAmountAdjustLog log = new EinvBossAmountAdjustLog();
        log.setTenantId(document.getTenantId());
        log.setDocumentId(document.getId());
        log.setOperatorId(requireOperatorId());
        log.setBeforeTotal(scale2(beforeTotal));
        log.setAfterTotal(scale2(targetTotal));
        log.setReason(reason);
        log.setDiffJson(JSON.toJSONString(buildDiffJson(existingLines, workingLines)));
        log.setCreatedAt(LocalDateTime.now());
        adjustLogRepository.save(log);

        return buildDetail(document);
    }

    private BossDocumentDetailDto buildDetail(EinvBossDocument document) {
        BossDocumentDetailDto dto = new BossDocumentDetailDto();
        dto.setDocumentId(document.getId());
        dto.setInvoiceId(document.getInvoiceId());
        dto.setPaymentId(document.getPaymentId());
        dto.setIssueKind(document.getIssueKind());
        dto.setIssueKindLabel(resolveIssueKindLabel(document.getIssueKind()));
        dto.setBusNo(document.getBusNo());
        dto.setTotalAmt(document.getTotalAmt());
        dto.setPaymentAmt(document.getPaymentAmt());
        dto.setAmountStatus(document.getAmountStatus());
        dto.setBossBillNo(document.getBossBillNo());
        dto.setBossBatchCode(document.getBossBatchCode());
        dto.setPictureUrl(document.getPictureUrl());
        dto.setPictureNetUrl(document.getPictureNetUrl());
        dto.setBillQrCode(document.getBillQrCode());
        if (IssueKind.COM.getCode().equals(document.getIssueKind())) {
            dto.setPatientName(document.getPayerName());
            dto.setGenderDisplay(document.getPayerSex());
            dto.setAge(document.getPayerAge());
            dto.setInvoiceStatus(StringUtils.hasText(document.getBossBillNo()) ? "ISSUED" : "DRAFT");
        } else {
            BossIssueSnapshot snapshot = healthInvoiceSnapshotReader.loadForInvoice(document.getInvoiceId());
            dto.setPatientName(snapshot.getPatientName());
            dto.setGenderDisplay(snapshot.getGenderDisplay());
            dto.setAge(snapshot.getAge());
            dto.setEncounterBusNo(snapshot.getEncounterBusNo());
            dto.setInvoiceStatus(snapshot.getInvoiceStatus());
            dto.setIssuedAt(snapshot.getIssuedAt());
        }

        List<EinvBossLine> lines = lineRepository.findByDocumentIdAndIsDeletedFalseOrderByLineNoAsc(document.getId());
        dto.setLines(lines.stream().map(this::toLineDto).collect(Collectors.toList()));

        taskRepository.findByInvoiceIdAndIsDeletedFalse(document.getInvoiceId()).ifPresent(task -> dto.setTask(toTaskDto(task)));

        dto.setAdjustLogs(adjustLogRepository.findByDocumentIdOrderByCreatedAtDesc(document.getId()).stream()
                .map(this::toAdjustLogDto).collect(Collectors.toList()));
        return dto;
    }

    private BossAmountValidateResultDto computeValidateResult(BigDecimal totalAmt, List<EinvBossLine> lines) {
        BigDecimal sum = BigDecimal.ZERO;
        for (EinvBossLine line : lines) {
            if (line.getAmount() != null) {
                sum = sum.add(line.getAmount());
            }
        }
        BigDecimal normalizedSum = scale2(sum);
        BigDecimal normalizedTotal = scale2(totalAmt);
        BossAmountValidateResultDto result = new BossAmountValidateResultDto();
        result.setLineSum(normalizedSum);
        result.setTotalAmt(normalizedTotal);
        result.setDiff(normalizedSum.subtract(normalizedTotal));
        result.setValid(normalizedSum.compareTo(normalizedTotal) == 0);
        return result;
    }

    private EinvBossDocument requireDocument(Long documentId) {
        return documentRepository.findById(documentId)
                .filter(doc -> !Boolean.TRUE.equals(doc.getIsDeleted()))
                .orElseThrow(() -> new BusinessException(EinvBossErrorCodes.EINV_DOCUMENT_NOT_FOUND, "无电子票据单据"));
    }

    private Long requireOperatorId() {
        return currentOperatorResolver.requireOperatorId("AUTH-001", "当前登录用户不能为空");
    }

    private BossLineDto toLineDto(EinvBossLine line) {
        BossLineDto dto = new BossLineDto();
        dto.setLineId(line.getId());
        dto.setLineNo(line.getLineNo());
        dto.setChargeCode(line.getChargeCode());
        dto.setChargeName(line.getChargeName());
        dto.setStdAmount(line.getStdAmount());
        dto.setAmount(line.getAmount());
        dto.setAdjusted(Boolean.TRUE.equals(line.getAdjusted()));
        return dto;
    }

    private BossTaskSummaryDto toTaskDto(EinvBossIssueTask task) {
        BossTaskSummaryDto dto = new BossTaskSummaryDto();
        dto.setTaskId(task.getId());
        dto.setStatus(task.getStatus());
        dto.setAttemptCount(task.getAttemptCount());
        dto.setLastErrorCode(task.getLastErrorCode());
        dto.setLastErrorMsg(task.getLastErrorMsg());
        dto.setNextRetryAt(task.getNextRetryAt());
        return dto;
    }

    private BossAdjustLogDto toAdjustLogDto(EinvBossAmountAdjustLog log) {
        BossAdjustLogDto dto = new BossAdjustLogDto();
        dto.setId(log.getId());
        dto.setBeforeTotal(log.getBeforeTotal());
        dto.setAfterTotal(log.getAfterTotal());
        dto.setReason(log.getReason());
        dto.setCreatedAt(log.getCreatedAt());
        return dto;
    }

    private Map<String, Object> buildDiffJson(List<EinvBossLine> before, List<EinvBossLine> after) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("beforeLineCount", before.size());
        payload.put("afterLineCount", after.size());
        return payload;
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

    private BigDecimal scale2(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
