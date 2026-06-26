package com.openhis.whale.einvoice.boss.application.reconcile;

import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.einvoice.boss.application.dto.CheckDailyTotalResponse;
import com.openhis.whale.einvoice.boss.application.request.CheckDailyTotalRequest;
import com.openhis.whale.einvoice.boss.contracts.constants.EinvBossErrorCodes;
import com.openhis.whale.einvoice.boss.contracts.dto.ReconcileLocalStatsDto;
import com.openhis.whale.einvoice.boss.contracts.enums.ReconcileDateMode;
import com.openhis.whale.einvoice.boss.contracts.reader.HealthInvoiceSnapshotReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 财务日结核对编排（P2）。
 */
@Service
@RequiredArgsConstructor
public class EinvoiceBossReconcileAppService {

    private final HealthInvoiceSnapshotReader healthInvoiceSnapshotReader;
    private final BossReconcileService bossReconcileService;

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

        ReconcileLocalStatsDto local = healthInvoiceSnapshotReader.countDailyReconcileStats(
                request.getCheckDate(), dateMode, issueKind);
        BossReconcileService.BossReconcileStats boss = bossReconcileService.queryBossTotal(
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

    private String normalizeDateMode(String dateMode) {
        if (ReconcileDateMode.IVC_DATE.getCode().equalsIgnoreCase(dateMode)) {
            return ReconcileDateMode.IVC_DATE.getCode();
        }
        return ReconcileDateMode.BUS_DATE.getCode();
    }
}
