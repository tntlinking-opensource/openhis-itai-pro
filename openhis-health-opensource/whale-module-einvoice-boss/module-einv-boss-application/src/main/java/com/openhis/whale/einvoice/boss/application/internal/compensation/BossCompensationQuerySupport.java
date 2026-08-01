/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.application.internal.compensation;

import com.openhis.whale.einvoice.boss.gateway.config.EinvoiceBossGatewayProperties;
import com.openhis.whale.einvoice.boss.application.compensation.response.BossPendingCompensationStats;
import com.openhis.whale.einvoice.boss.application.compensation.request.ListPendingCompensationRequest;
import com.openhis.whale.einvoice.boss.domain.reconcile.result.BossCompensationRow;
import com.openhis.whale.einvoice.boss.domain.issue.result.BossIssueSnapshot;
import com.openhis.whale.einvoice.boss.domain.enums.CompensationExceptionType;
import com.openhis.whale.einvoice.boss.domain.enums.IssueKind;
import com.openhis.whale.einvoice.boss.domain.enums.TaskStatus;
import com.openhis.whale.einvoice.boss.domain.issue.loader.EinvoiceIssueSnapshotContextLoader;
import com.openhis.whale.einvoice.boss.domain.document.entity.EinvBossDocument;
import com.openhis.whale.einvoice.boss.domain.document.repository.EinvBossDocumentRepository;
import com.openhis.whale.einvoice.boss.domain.issue.entity.EinvBossIssueTask;
import com.openhis.whale.einvoice.boss.domain.issue.repository.EinvBossIssueTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
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
 * 待补偿列表查询支撑（供 {@link com.openhis.whale.einvoice.boss.application.compensation.service.EinvoiceBossCompensationAppService} 使用）。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BossCompensationQuerySupport {

    private final EinvoiceIssueSnapshotContextLoader issueSnapshotContextLoader;
    private final EinvBossDocumentRepository documentRepository;
    private final EinvBossIssueTaskRepository taskRepository;
    private final EinvoiceBossGatewayProperties properties;

    private boolean lastScanTruncated;

    /**
     * 加载待补偿行（含筛选）。
     *
     * @param filter 筛选条件
     * @return 待补偿行
     */
    public List<BossCompensationRow> loadCompensationRows(ListPendingCompensationRequest filter) {
        LocalDateTime now = LocalDateTime.now();
        int staleMinutes = properties.getCompensation().getStaleMinutes();
        Set<Long> invoiceIds = collectCandidateInvoiceIds(now.minusMinutes(staleMinutes));
        List<BossCompensationRow> rows = new ArrayList<>();
        for (Long invoiceId : invoiceIds) {
            try {
                BossIssueSnapshot snapshot = issueSnapshotContextLoader.loadForInvoice(invoiceId);
                Optional<EinvBossIssueTask> task = taskRepository.findByInvoiceIdAndIsDeletedFalse(invoiceId);
                Optional<CompensationExceptionType> type = BossCompensationClassifier.classify(
                        snapshot, task, staleMinutes, now);
                if (type.isEmpty()) {
                    continue;
                }
                BossCompensationRow row = toRow(
                        snapshot,
                        task,
                        documentRepository.findByInvoiceIdAndIsDeletedFalse(invoiceId),
                        type.get(),
                        now);
                if (!matchesFilter(row, filter)) {
                    continue;
                }
                rows.add(row);
            } catch (Exception ignored) {
                log.debug("Compensation row skipped, invoiceId={}", invoiceId);
            }
        }
        rows.sort(Comparator.comparingLong(BossCompensationRow::getWaitingMinutes).reversed());
        return rows;
    }

    /**
     * 最近一次扫描是否因上限截断。
     *
     * @return 截断标记
     */
    public boolean isLastScanTruncated() {
        return lastScanTruncated;
    }

    /**
     * 汇总待补偿统计。
     *
     * @param rows 待补偿行
     * @return 统计
     */
    public BossPendingCompensationStats toStats(List<BossCompensationRow> rows) {
        BossPendingCompensationStats stats = new BossPendingCompensationStats();
        stats.setPendingCount(rows.size());
        for (BossCompensationRow row : rows) {
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

    /**
     * 汇总 orphan DRAFT、超时 DRAFT、FAILED/PENDING 任务的候选 invoiceId（受 scanLimit 约束）。
     *
     * @param staleThreshold 超时阈值
     * @return 去重后的候选集合；达上限时 {@link #lastScanTruncated}=true
     */
    private Set<Long> collectCandidateInvoiceIds(LocalDateTime staleThreshold) {
        int scanLimit = properties.getCompensation().getScanLimit();
        Set<Long> invoiceIds = new LinkedHashSet<>();
        invoiceIds.addAll(issueSnapshotContextLoader.findOrphanDraftInvoiceIds(scanLimit));
        invoiceIds.addAll(issueSnapshotContextLoader.findDraftInvoiceIdsBefore(staleThreshold, scanLimit));
        for (EinvBossIssueTask task : taskRepository.findByStatusAndIsDeletedFalse(TaskStatus.FAILED.getCode())) {
            if (invoiceIds.size() >= scanLimit) {
                break;
            }
            invoiceIds.add(task.getInvoiceId());
        }
        for (EinvBossIssueTask task : taskRepository.findByStatusAndIsDeletedFalse(TaskStatus.PENDING.getCode())) {
            if (invoiceIds.size() >= scanLimit) {
                break;
            }
            invoiceIds.add(task.getInvoiceId());
        }
        lastScanTruncated = invoiceIds.size() >= scanLimit;
        return invoiceIds;
    }

    /** 按异常类型、业务线、关键词过滤补偿行。 */
    private boolean matchesFilter(BossCompensationRow row, ListPendingCompensationRequest filter) {
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
        return true;
    }

    /** 快照 + 分类结果 → 补偿列表行（含可操作按钮标志）。 */
    private BossCompensationRow toRow(
            BossIssueSnapshot snapshot,
            Optional<EinvBossIssueTask> task,
            Optional<EinvBossDocument> document,
            CompensationExceptionType type,
            LocalDateTime now) {
        BossCompensationRow row = new BossCompensationRow();
        row.setInvoiceId(snapshot.getInvoiceId());
        row.setPaymentId(snapshot.getPaymentId());
        document.ifPresent(doc -> row.setDocumentId(doc.getId()));
        row.setPatientName(snapshot.getPatientName());
        row.setEncounterBusNo(snapshot.getEncounterBusNo());
        row.setIssueKind(resolveIssueKind(snapshot));
        row.setIssueKindLabel(resolveIssueKindLabel(row.getIssueKind()));
        row.setTotalAmt(snapshot.getTotalAmt());
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
        fillActions(row, type, snapshot, document);
        return row;
    }

    /** 按异常类型设置前端可执行动作（重新入队/立即开/查票/调额）。 */
    private void fillActions(
            BossCompensationRow row,
            CompensationExceptionType type,
            BossIssueSnapshot snapshot,
            Optional<EinvBossDocument> document) {
        switch (type) {
            case LOST -> {
                row.setCanRequeue(true);
                row.setCanRecoverByBusNo(true);
            }
            case AMOUNT -> {
                if (isAdjustable(snapshot, document)) {
                    row.setCanAdjustAmount(true);
                }
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

    /** 与单据调额门控一致：仅草稿（未成功开出）允许调额。 */
    private boolean isAdjustable(BossIssueSnapshot snapshot, Optional<EinvBossDocument> document) {
        if (document.isPresent() && IssueKind.COM.getCode().equals(document.get().getIssueKind())) {
            return !StringUtils.hasText(document.get().getBossBillNo());
        }
        return "DRAFT".equals(snapshot.getInvoiceStatus());
    }

    /** 由 health invoiceTypeCode 推断 REG/AMB/IMP。 */
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

    /** 异常类型枚举 → 列表展示文案。 */
    private String resolveExceptionLabel(CompensationExceptionType type) {
        return switch (type) {
            case LOST -> "事件丢失";
            case AMOUNT -> "金额待调整";
            case FAILED -> "开票失败";
            case STALE -> "超时待查";
        };
    }
}
