/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.reconciliation.invoker;

import com.openhis.chs.bridge.reconciliation.support.ReconPlatformSupport;
import com.openhis.chs.bridge.reconciliation.support.ReconPlatformSupport.ReconContractContext;
import com.openhis.chs.domain.reconciliation.command.ApplyJilinClear3203ACommand;
import com.openhis.chs.domain.reconciliation.result.ApplyJilinClear3203AResult;
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
 * 【3203A】吉林清算申请 Invoker：gateway 出站；成功后 saveAppliedClearApply 落本地清算单。
 * <p>实现 domain {@link com.openhis.chs.domain.reconciliation.access.Recon3203AInvoker}。</p>
 */
@Component
@RequiredArgsConstructor
public class Recon3203AInvoker implements com.openhis.chs.domain.reconciliation.access.Recon3203AInvoker {

    private final InsuranceGatewayClient gatewayClient;
    private final ReconPlatformSupport reconPlatformSupport;
    private final YbReconciliationDomainService reconciliationDomainService;

    @Override
    public ApplyJilinClear3203AResult invoke(ApplyJilinClear3203ACommand command) {
        ApplyJilinClear3203ACommand safe = command == null ? new ApplyJilinClear3203ACommand() : command;
        ReconContractContext ctx = reconPlatformSupport.resolveContract(safe.getContractBusNo(), safe.getContractId());
        ResultBody body = gatewayClient.invoke(
            ctx.busNo(),
            ChsInfNo.CLEAR_APPLY_JILIN,
            ReconPlatformInputAssembler.assemble3203(safe),
            reconPlatformSupport.buildInvokeContext());
        ChsPlatformResponseSupport.requireSuccess(body, ChsInfNo.CLEAR_APPLY_JILIN);
        String evtId = ReconPlatformOutputParser.parse3203ClrAppyEvtId(body);
        LocalDate begin = reconPlatformSupport.parseDate(safe.getBegndate(), "开始日期");
        LocalDate end = reconPlatformSupport.parseDate(safe.getEnddate(), "结束日期");
        var row = reconciliationDomainService.saveAppliedClearApply(
            ctx.contract().id(), ctx.busNo(), safe, evtId, begin, end);
        return new ApplyJilinClear3203AResult().setClrAppyEvtId(evtId).setLocalRecordId(row.getId());
    }
}
