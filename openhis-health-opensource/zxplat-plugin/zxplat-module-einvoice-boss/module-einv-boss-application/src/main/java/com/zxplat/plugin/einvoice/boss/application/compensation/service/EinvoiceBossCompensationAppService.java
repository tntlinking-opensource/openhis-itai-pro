/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.application.compensation.service;

import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.plugin.einvoice.boss.application.compensation.response.BatchCompensateResult;
import com.zxplat.plugin.einvoice.boss.application.compensation.response.BossPendingCompensationStats;
import com.zxplat.plugin.einvoice.boss.application.compensation.response.ListPendingCompensationResponse;
import com.zxplat.plugin.einvoice.boss.application.compensation.request.BatchInvoiceIdsRequest;
import com.zxplat.plugin.einvoice.boss.application.compensation.request.ListPendingCompensationRequest;
import com.zxplat.plugin.einvoice.boss.application.internal.compensation.BossCompensationQuerySupport;
import com.zxplat.plugin.einvoice.boss.application.internal.issue.EinvoiceBossIssueService;
import com.zxplat.plugin.einvoice.boss.domain.share.EinvBossErrorCodes;
import com.zxplat.plugin.einvoice.boss.domain.reconcile.result.BossCompensationRow;
import com.zxplat.plugin.einvoice.boss.domain.issue.result.BossInvoiceIssueResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 待补偿开票页（HTTP：{@code /api/app/einvoiceBossCompensation/*}）。
 *
 * <p>扫描 DRAFT/失败任务、批量重新入队/立即开具、按 busNo 查票补偿。</p>
 */
@Service
@RequiredArgsConstructor
public class EinvoiceBossCompensationAppService {

    private final BossCompensationQuerySupport compensationQuerySupport;
    private final EinvoiceBossIssueService issueAppService;

    /**
     * 待补偿统计（角标/顶栏）。
     *
     * @return 统计
     */
    public BossPendingCompensationStats getPendingCompensationStats() {
        List<BossCompensationRow> rows = compensationQuerySupport.loadCompensationRows(null);
        BossPendingCompensationStats stats = compensationQuerySupport.toStats(rows);
        stats.setScanTruncated(compensationQuerySupport.isLastScanTruncated());
        return stats;
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
    public ListPendingCompensationResponse listPendingCompensation(ListPendingCompensationRequest request) {
        ListPendingCompensationRequest safe = request == null ? new ListPendingCompensationRequest() : request;
        List<BossCompensationRow> filtered = compensationQuerySupport.loadCompensationRows(safe);
        ListPendingCompensationResponse response = new ListPendingCompensationResponse();
        response.setStats(compensationQuerySupport.toStats(filtered));
        response.getStats().setScanTruncated(compensationQuerySupport.isLastScanTruncated());

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

    /**
     * 待补偿「重新入队」。
     *
     * @param invoiceId 票据主键
     */
    public void compensateRequeue(Long invoiceId) {
        issueAppService.compensateRequeue(invoiceId);
    }

    /**
     * 立即同步开具一次。
     *
     * @param invoiceId 票据主键
     * @return 开具结果
     */
    public BossInvoiceIssueResult issueNow(Long invoiceId) {
        return issueAppService.issueNow(invoiceId);
    }

    /**
     * 按 busNo 查票补偿。
     *
     * @param invoiceId 票据主键
     * @return 查票结果
     */
    public Optional<BossInvoiceIssueResult> recoverByBusNo(Long invoiceId) {
        return issueAppService.recoverByBusNo(invoiceId);
    }

    /**
     * 批量重新入队。
     *
     * @param request 票据主键列表
     * @return 批量结果
     */
    public BatchCompensateResult batchCompensateRequeue(BatchInvoiceIdsRequest request) {
        BatchCompensateResult result = new BatchCompensateResult();
        if (request == null || request.getInvoiceIds() == null || request.getInvoiceIds().isEmpty()) {
            throw new BusinessException(EinvBossErrorCodes.EINV_TASK_NOT_FOUND, "请选择待补开票据");
        }
        for (Long invoiceId : request.getInvoiceIds()) {
            BatchCompensateResult.BatchCompensateItemDto item = new BatchCompensateResult.BatchCompensateItemDto();
            item.setInvoiceId(invoiceId);
            try {
                issueAppService.compensateRequeue(invoiceId);
                item.setSuccess(true);
                result.setSuccessCount(result.getSuccessCount() + 1);
            } catch (Exception ex) {
                item.setSuccess(false);
                item.setMessage(ex.getMessage());
                result.setFailCount(result.getFailCount() + 1);
            }
            result.getItems().add(item);
        }
        return result;
    }

    /**
     * 批量立即开具。
     *
     * @param request 票据主键列表
     * @return 批量结果
     */
    public BatchCompensateResult batchIssueNow(BatchInvoiceIdsRequest request) {
        BatchCompensateResult result = new BatchCompensateResult();
        if (request == null || request.getInvoiceIds() == null || request.getInvoiceIds().isEmpty()) {
            throw new BusinessException(EinvBossErrorCodes.EINV_TASK_NOT_FOUND, "请选择待补开票据");
        }
        for (Long invoiceId : request.getInvoiceIds()) {
            BatchCompensateResult.BatchCompensateItemDto item = new BatchCompensateResult.BatchCompensateItemDto();
            item.setInvoiceId(invoiceId);
            try {
                BossInvoiceIssueResult issueResult = issueAppService.issueNow(invoiceId);
                item.setSuccess(true);
                if (issueResult != null) {
                    item.setBillNo(issueResult.getBillNo());
                }
                result.setSuccessCount(result.getSuccessCount() + 1);
            } catch (Exception ex) {
                item.setSuccess(false);
                item.setMessage(ex.getMessage());
                result.setFailCount(result.getFailCount() + 1);
            }
            result.getItems().add(item);
        }
        return result;
    }
}
