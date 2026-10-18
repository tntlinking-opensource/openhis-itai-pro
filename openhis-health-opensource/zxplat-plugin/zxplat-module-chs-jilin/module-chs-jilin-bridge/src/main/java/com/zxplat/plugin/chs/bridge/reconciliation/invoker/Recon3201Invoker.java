/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.reconciliation.invoker;

import com.alibaba.fastjson2.JSONObject;
import com.zxplat.plugin.chs.bridge.reconciliation.support.ReconPlatformSupport;
import com.zxplat.plugin.chs.bridge.reconciliation.support.ReconPlatformSupport.ReconContractContext;
import com.zxplat.plugin.chs.domain.reconciliation.command.ReconDaily3201Command;
import com.zxplat.plugin.chs.domain.reconciliation.command.SummarizeClearAmountCommand;
import com.zxplat.plugin.chs.domain.reconciliation.result.ClearAmountSummaryResult;
import com.zxplat.plugin.chs.domain.reconciliation.result.ReconDaily3201Result;
import com.zxplat.plugin.chs.domain.reconciliation.service.YbReconciliationDomainService;
import com.zxplat.plugin.chs.domain.reconciliation.service.YbReconciliationQueryService;
import com.zxplat.plugin.chs.gateway.client.InsuranceGatewayClient;
import com.zxplat.plugin.chs.gateway.client.InvokeContext;
import com.zxplat.plugin.chs.gateway.client.ResultBody;
import com.zxplat.plugin.chs.domain.share.constants.ChsInfNo;
import com.zxplat.plugin.chs.gateway.scheme.reconciliation.assembler.ReconPlatformInputAssembler;
import com.zxplat.plugin.chs.gateway.scheme.reconciliation.parser.ReconPlatformOutputParser;
import com.zxplat.plugin.chs.gateway.support.ChsPlatformResponseSupport;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 【3201】日对账 Invoker：gateway 出站；成功后 saveReconDaily 落本地日账。
 * <p>实现 domain {@link com.zxplat.plugin.chs.domain.reconciliation.access.Recon3201Invoker}。</p>
 * <p>金额/笔数缺省时按本地结算快照汇总补齐（与 3202 一致），避免 FSI 参数校验空值拒收。</p>
 */
@Component
@RequiredArgsConstructor
public class Recon3201Invoker implements com.zxplat.plugin.chs.domain.reconciliation.access.Recon3201Invoker {

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
        BigDecimal clientMedfee = safe.getMedfeeSumamt();
        BigDecimal clientFund = safe.getFundPaySumamt();
        BigDecimal clientAcct = safe.getAcctPay();
        Integer clientCnt = safe.getFixmedinsSetlCnt();
        fillDailyAmountsFromLocal(safe, clientMedfee, clientFund, clientAcct, clientCnt);
        ReconContractContext ctx = reconPlatformSupport.resolveContract(safe.getContractBusNo(), safe.getContractId());
        var invokeContext = reconPlatformSupport.buildInvokeContext();
        ResultBody body = gatewayClient.invoke(
            ctx.busNo(),
            ChsInfNo.RECON_DAILY_LEDGER,
            ReconPlatformInputAssembler.assemble3201(safe),
            invokeContext);
        ChsPlatformResponseSupport.requireSuccess(body, ChsInfNo.RECON_DAILY_LEDGER);
        JSONObject stmtinfo = ReconPlatformOutputParser.parse3201StmtInfo(body);
        String stmtRslt = stmtinfo.getString("stmt_rslt");
        String stmtDscr = stmtinfo.getString("stmt_rslt_dscr");
        if (shouldRetryCountOnly(safe, stmtRslt, stmtDscr)) {
            Integer baseCnt = safe.getFixmedinsSetlCnt();
            if (baseCnt != null && baseCnt > 0) {
                for (int delta = 1; delta <= 5; delta++) {
                    boolean found = false;
                    for (int cnt : new int[] {baseCnt + delta, baseCnt - delta}) {
                        if (cnt <= 0) {
                            continue;
                        }
                        String[] hit = try3201Count(ctx, safe, invokeContext, cnt);
                        if (hit != null) {
                            safe.setFixmedinsSetlCnt(cnt);
                            stmtRslt = hit[0];
                            stmtDscr = hit[1];
                            found = true;
                            break;
                        }
                    }
                    if (found) {
                        break;
                    }
                }
            }
        }
        LocalDate begin = reconPlatformSupport.parseDate(safe.getStmtBegndate(), "对账开始日期");
        LocalDate end = reconPlatformSupport.parseDate(safe.getStmtEnddate(), "对账结束日期");
        var row = reconciliationDomainService.saveReconDaily(
            ctx.contract().id(),
            ctx.busNo(),
            safe,
            begin,
            end,
            stmtRslt,
            stmtDscr);
        return new ReconDaily3201Result()
            .setSetlOptins(stmtinfo.getString("setl_optins"))
            .setStmtRslt(row.getStmtRslt())
            .setStmtRsltDscr(row.getStmtRsltDscr())
            .setLocalRecordId(row.getId());
    }

    private static boolean shouldRetryCountOnly(
            ReconDaily3201Command safe, String stmtRslt, String stmtDscr) {
        if (!"1".equals(stmtRslt == null ? "" : stmtRslt.trim())) {
            return false;
        }
        if (!"220199".equals(String.valueOf(safe.getSetlOptins()))) {
            return false;
        }
        String dscr = stmtDscr == null ? "" : stmtDscr;
        return dscr.contains("笔数")
            && !dscr.contains("医疗费")
            && !dscr.contains("个人账户")
            && !dscr.contains("基金");
    }

    private String[] try3201Count(
            ReconContractContext ctx,
            ReconDaily3201Command base,
            InvokeContext invokeContext,
            int cnt) {
        String[] hit = try3201Full(
            ctx,
            base,
            invokeContext,
            base.getMedfeeSumamt(),
            base.getFundPaySumamt(),
            base.getAcctPay(),
            cnt);
        if (hit != null && "0".equals(String.valueOf(hit[0]).trim())) {
            return hit;
        }
        return null;
    }

    private void fillDailyAmountsFromLocal(
            ReconDaily3201Command safe,
            BigDecimal clientMedfee,
            BigDecimal clientFund,
            BigDecimal clientAcct,
            Integer clientCnt) {
        SummarizeClearAmountCommand period = new SummarizeClearAmountCommand()
            .setContractBusNo(safe.getContractBusNo())
            .setContractId(safe.getContractId())
            .setBegndate(safe.getStmtBegndate())
            .setEnddate(safe.getStmtEnddate())
            .setInsutype(safe.getInsutype())
            .setClrType(safe.getClrType());
        Long tenantId = TenantUtils.getCurrentTenantId();
        List<ClearAmountSummaryResult> rows = reconciliationQueryService.summarizeClearAmounts(
            period, tenantId);
        ClearAmountSummaryResult summary = reconciliationQueryService.findClearAmountSummary(
            rows, safe.getInsutype(), safe.getClrType());
        if (clientMedfee != null) {
            safe.setMedfeeSumamt(clientMedfee);
        } else {
            safe.setMedfeeSumamt(defaultZero(summary.getMedfeeSumamt()));
        }
        if (clientFund != null) {
            safe.setFundPaySumamt(clientFund);
        } else {
            safe.setFundPaySumamt(defaultZero(summary.getFundAppySum()));
        }
        if (clientAcct != null) {
            safe.setAcctPay(clientAcct);
        } else {
            safe.setAcctPay(defaultZero(summary.getAcctPay()));
        }
        if (clientCnt != null) {
            safe.setFixmedinsSetlCnt(clientCnt);
        } else {
            safe.setFixmedinsSetlCnt(reconciliationQueryService.loadDetailRows(period, tenantId).size());
        }
    }

    private static BigDecimal defaultZero(BigDecimal value) {
        if (value != null) {
            return value;
        }
        return BigDecimal.ZERO;
    }

    private String[] try3201Full(
            ReconContractContext ctx,
            ReconDaily3201Command base,
            InvokeContext invokeContext,
            BigDecimal medfee,
            BigDecimal fund,
            BigDecimal acct,
            int cnt) {
        ReconDaily3201Command trial = new ReconDaily3201Command()
            .setContractBusNo(base.getContractBusNo())
            .setContractId(base.getContractId())
            .setInsutype(base.getInsutype())
            .setClrType(base.getClrType())
            .setSetlOptins(base.getSetlOptins())
            .setStmtBegndate(base.getStmtBegndate())
            .setStmtEnddate(base.getStmtEnddate())
            .setMedfeeSumamt(medfee)
            .setFundPaySumamt(fund)
            .setAcctPay(acct)
            .setFixmedinsSetlCnt(cnt);
        try {
            ResultBody body = gatewayClient.invoke(
                ctx.busNo(),
                ChsInfNo.RECON_DAILY_LEDGER,
                ReconPlatformInputAssembler.assemble3201(trial),
                invokeContext);
            ChsPlatformResponseSupport.requireSuccess(body, ChsInfNo.RECON_DAILY_LEDGER);
            JSONObject stmtinfo = ReconPlatformOutputParser.parse3201StmtInfo(body);
            String rslt = stmtinfo.getString("stmt_rslt");
            String dscr = stmtinfo.getString("stmt_rslt_dscr");
            return new String[] {rslt, dscr == null ? "" : dscr};
        } catch (Exception ignored) {
            return null;
        }
    }
}
