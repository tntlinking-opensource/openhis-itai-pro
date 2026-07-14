/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.reconciliation.invoker;

import com.alibaba.fastjson2.JSONObject;
import com.openhis.chs.bridge.reconciliation.support.ReconPlatformSupport;
import com.openhis.chs.bridge.reconciliation.support.ReconPlatformSupport.ReconContractContext;
import com.openhis.chs.domain.reconciliation.command.ReconDaily3201Command;
import com.openhis.chs.domain.reconciliation.result.ReconDaily3201Result;
import com.openhis.chs.domain.reconciliation.service.YbReconciliationDomainService;
import com.openhis.chs.gateway.client.InsuranceGatewayClient;
import com.openhis.chs.gateway.client.ResultBody;
import com.openhis.chs.domain.share.constants.ChsInfNo;
import com.openhis.chs.gateway.scheme.reconciliation.assembler.ReconPlatformInputAssembler;
import com.openhis.chs.gateway.scheme.reconciliation.parser.ReconPlatformOutputParser;
import com.openhis.chs.gateway.support.ChsPlatformResponseSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 【3201】日对账 Invoker：gateway 出站；成功后 saveReconDaily 落本地日账。
 * <p>实现 domain {@link com.openhis.chs.domain.reconciliation.access.Recon3201Invoker}。</p>
 */
@Component
@RequiredArgsConstructor
public class Recon3201Invoker implements com.openhis.chs.domain.reconciliation.access.Recon3201Invoker {

    private final InsuranceGatewayClient gatewayClient;
    private final ReconPlatformSupport reconPlatformSupport;
    private final YbReconciliationDomainService reconciliationDomainService;

    @Override
    public ReconDaily3201Result invoke(ReconDaily3201Command command) {
        ReconDaily3201Command safe = command == null ? new ReconDaily3201Command() : command;
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
}
