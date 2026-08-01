/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.reconciliation.invoker;

import com.alibaba.fastjson2.JSONObject;
import com.openhis.chs.bridge.reconciliation.support.ReconPlatformSupport;
import com.openhis.chs.bridge.reconciliation.support.ReconPlatformSupport.ReconContractContext;
import com.openhis.chs.domain.reconciliation.command.ReconDaily3201Command;
import com.openhis.chs.domain.reconciliation.command.SummarizeClearAmountCommand;
import com.openhis.chs.domain.reconciliation.result.ClearAmountSummaryResult;
import com.openhis.chs.domain.reconciliation.result.ReconDaily3201Result;
import com.openhis.chs.domain.reconciliation.service.YbReconciliationDomainService;
import com.openhis.chs.domain.reconciliation.service.YbReconciliationQueryService;
import com.openhis.chs.gateway.client.InsuranceGatewayClient;
import com.openhis.chs.gateway.client.ResultBody;
import com.openhis.chs.domain.share.constants.ChsInfNo;
import com.openhis.chs.gateway.scheme.reconciliation.assembler.ReconPlatformInputAssembler;
import com.openhis.chs.gateway.scheme.reconciliation.parser.ReconPlatformOutputParser;
import com.openhis.chs.gateway.support.ChsPlatformResponseSupport;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 【3201】日对账 Invoker：gateway 出站；成功后 saveReconDaily 落本地日账。
 * <p>实现 domain {@link com.openhis.chs.domain.reconciliation.access.Recon3201Invoker}。</p>
 * <p>金额/笔数缺省时按本地结算快照汇总补齐（与 3202 一致），避免 FSI 参数校验空值拒收。</p>
 */
@Component
@RequiredArgsConstructor
public class Recon3201Invoker implements com.openhis.chs.domain.reconciliation.access.Recon3201Invoker {

    private final InsuranceGatewayClient gatewayClient;
    private final ReconPlatformSupport reconPlatformSupport;
    private final YbReconciliationDomainService reconciliationDomainService;
    private final YbReconciliationQueryService reconciliationQueryService;

    @Override
    public ReconDaily3201Result invoke(ReconDaily3201Command command) {
        ReconDaily3201Command safe;
        if (command == null) {
            safe = new ReconDaily3201Command();
        } else {
            safe = command;
        }
        fillDailyAmountsIfAbsent(safe);
        ReconContractContext ctx = reconPlatformSupport.resolveContract(safe.getContractBusNo(), safe.getContractId());
        ResultBody body = gatewayClient.invoke(
            ctx.busNo(),
            ChsInfNo.RECON_DAILY_LEDGER,
            ReconPlatformInputAssembler.assemble3201(safe),
            reconPlatformSupport.buildInvokeContext());
        ChsPlatformResponseSupport.requireSuccess(body, ChsInfNo.RECON_DAILY_LEDGER);
        JSONObject stmtinfo = ReconPlatformOutputParser.parse3201StmtInfo(body);
        LocalDate begin = reconPlatformSupport.parseDate(safe.getStmtBegndate(), "对账开始日期");
        LocalDate end = reconPlatformSupport.parseDate(safe.getStmtEnddate(), "对账结束日期");
        var row = reconciliationDomainService.saveReconDaily(
            ctx.contract().id(),
            ctx.busNo(),
            safe,
            begin,
            end,
            stmtinfo.getString("stmt_rslt"),
            stmtinfo.getString("stmt_rslt_dscr"));
        return new ReconDaily3201Result()
            .setSetlOptins(stmtinfo.getString("setl_optins"))
            .setStmtRslt(row.getStmtRslt())
            .setStmtRsltDscr(row.getStmtRsltDscr())
            .setLocalRecordId(row.getId());
    }

    /**
     * 医疗费/基金/个账/笔数任一为空时，按本行险种 + 清算类别汇总本地结算后补齐；仍空则置 0。
     *
     * @param safe 已空安全的 3201 命令
     */
    private void fillDailyAmountsIfAbsent(ReconDaily3201Command safe) {
        if (safe.getMedfeeSumamt() != null
            && safe.getFundPaySumamt() != null
            && safe.getAcctPay() != null
            && safe.getFixmedinsSetlCnt() != null) {
            return;
        }
        // Pre：仅汇总当前 3201 维度，避免把其它险种金额误填入本行
        SummarizeClearAmountCommand period = new SummarizeClearAmountCommand()
            .setContractBusNo(safe.getContractBusNo())
            .setContractId(safe.getContractId())
            .setBegndate(safe.getStmtBegndate())
            .setEnddate(safe.getStmtEnddate())
            .setInsutype(safe.getInsutype())
            .setClrType(safe.getClrType());
        List<ClearAmountSummaryResult> rows = reconciliationQueryService.summarizeClearAmounts(
            period, TenantUtils.getCurrentTenantId());
        ClearAmountSummaryResult summary = reconciliationQueryService.findClearAmountSummary(
            rows, safe.getInsutype(), safe.getClrType());
        if (safe.getMedfeeSumamt() == null) {
            safe.setMedfeeSumamt(defaultZero(summary.getMedfeeSumamt()));
        }
        if (safe.getFundPaySumamt() == null) {
            safe.setFundPaySumamt(defaultZero(summary.getFundAppySum()));
        }
        if (safe.getAcctPay() == null) {
            safe.setAcctPay(defaultZero(summary.getAcctPay()));
        }
        if (safe.getFixmedinsSetlCnt() == null) {
            Integer setlCount = summary.getSetlCount();
            if (setlCount != null) {
                safe.setFixmedinsSetlCnt(setlCount);
            } else {
                safe.setFixmedinsSetlCnt(0);
            }
        }
    }

    /**
     * 空金额按 0 补齐，满足 FSI 非空校验。
     *
     * @param value 原始金额
     * @return 非 null 金额
     */
    private static BigDecimal defaultZero(BigDecimal value) {
        if (value != null) {
            return value;
        }
        return BigDecimal.ZERO;
    }
}
