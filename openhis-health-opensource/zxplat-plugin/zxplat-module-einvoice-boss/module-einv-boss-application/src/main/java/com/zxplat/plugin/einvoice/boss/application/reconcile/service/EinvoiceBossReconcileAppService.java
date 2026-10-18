/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.application.reconcile.service;

import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.plugin.einvoice.boss.application.reconcile.response.CheckDailyTotalResponse;
import com.zxplat.plugin.einvoice.boss.application.reconcile.request.CheckDailyTotalRequest;
import com.zxplat.plugin.einvoice.boss.application.support.BossTenantSupport;
import com.zxplat.plugin.einvoice.boss.domain.reconcile.access.BossReconcileQueryAccess;
import com.zxplat.plugin.einvoice.boss.domain.share.EinvBossErrorCodes;
import com.zxplat.plugin.einvoice.boss.domain.reconcile.result.BossReconcileTotalResult;
import com.zxplat.plugin.einvoice.boss.domain.reconcile.result.ReconcileLocalStats;
import com.zxplat.plugin.einvoice.boss.domain.enums.ReconcileDateMode;
import com.zxplat.plugin.einvoice.boss.domain.issue.loader.EinvoiceIssueSnapshotContextLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

/**
 * 财务日结核对页（HTTP：{@code /api/app/einvoiceBossReconcile/*}）。
 *
 * <p>对比 health 本地已开具统计与博思 {@code checkTotalData*} 平台汇总。</p>
 */
@Service
@RequiredArgsConstructor
public class EinvoiceBossReconcileAppService {

    private final EinvoiceIssueSnapshotContextLoader issueSnapshotContextLoader;
    private final BossReconcileQueryAccess bossReconcileQueryAccess;

    /**
     * 核对指定日期的本地已开具笔数与博思总笔数。
     *
     * @param request 核对条件
     * @return 对比结果
     */
    public CheckDailyTotalResponse checkDailyTotal(CheckDailyTotalRequest request) {
        if (request == null || request.getCheckDate() == null) {
            throw new BusinessException(EinvBossErrorCodes.EINV_BOSS_REJECT, "核对日期不能为空");
        }
        String dateMode = normalizeDateMode(request.getDateMode());
        String issueKind = StringUtils.hasText(request.getIssueKind()) ? request.getIssueKind().trim() : null;
        int pageNo = request.getPageNo() == null || request.getPageNo() < 1 ? 1 : request.getPageNo();
        int pageSize = request.getPageSize() == null || request.getPageSize() < 1 ? 200 : request.getPageSize();

        ReconcileLocalStats local = issueSnapshotContextLoader.countDailyReconcileStats(
                request.getCheckDate(), dateMode, issueKind);
        BossReconcileTotalResult boss = bossReconcileQueryAccess.queryBossTotal(
                BossTenantSupport.requireCurrentTenantId(),
                request.getCheckDate(), dateMode, issueKind, pageNo, pageSize);

        CheckDailyTotalResponse response = new CheckDailyTotalResponse();
        response.setCheckDate(request.getCheckDate());
        response.setDateMode(dateMode);
        response.setIssueKind(issueKind);
        response.setLocalIssuedCount(local.getIssuedCount());
        response.setLocalIssuedAmount(local.getIssuedTotalAmt() != null ? local.getIssuedTotalAmt() : BigDecimal.ZERO);
        response.setBossTotalCount(boss.getTotalCount());
        response.setBossTotalAmount(boss.getTotalAmount() != null ? boss.getTotalAmount() : BigDecimal.ZERO);
        response.setBossSuccess(boss.isSuccess());
        response.setBossResultCode(boss.getResultCode());
        response.setBossMessage(boss.getRawMessage());
        response.setCountDiff(local.getIssuedCount() - boss.getTotalCount());
        response.setMatched(boss.isSuccess() && local.getIssuedCount() == boss.getTotalCount());
        return response;
    }

    /** 归一化日期模式：默认 BUS_DATE（按支付业务日），否则 IVC_DATE（按开具日）。 */
    private String normalizeDateMode(String dateMode) {
        if (ReconcileDateMode.IVC_DATE.getCode().equalsIgnoreCase(dateMode)) {
            return ReconcileDateMode.IVC_DATE.getCode();
        }
        return ReconcileDateMode.BUS_DATE.getCode();
    }
}
