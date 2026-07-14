/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.reconciliation.invoker;

import com.openhis.chs.bridge.reconciliation.support.ReconPlatformSupport;
import com.openhis.chs.bridge.reconciliation.support.ReconPlatformSupport.ReconContractContext;
import com.openhis.chs.domain.reconciliation.command.QueryClearStatus3205ACommand;
import com.openhis.chs.domain.reconciliation.result.ClearStatus3205ARowResult;
import com.openhis.chs.gateway.client.InsuranceGatewayClient;
import com.openhis.chs.gateway.client.ResultBody;
import com.openhis.chs.domain.share.constants.ChsInfNo;
import com.openhis.chs.gateway.scheme.reconciliation.assembler.ReconPlatformInputAssembler;
import com.openhis.chs.gateway.scheme.reconciliation.parser.ReconPlatformOutputParser;
import com.openhis.chs.gateway.support.ChsPlatformResponseSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 【3205A】清算申请状态查询 Invoker：gateway 出站（只读，不落库）。
 * <p>实现 domain {@link com.openhis.chs.domain.reconciliation.access.Recon3205AInvoker}。</p>
 */
@Component
@RequiredArgsConstructor
public class Recon3205AInvoker implements com.openhis.chs.domain.reconciliation.access.Recon3205AInvoker {

    private final InsuranceGatewayClient gatewayClient;
    private final ReconPlatformSupport reconPlatformSupport;

    @Override
    public List<ClearStatus3205ARowResult> invoke(QueryClearStatus3205ACommand command) {
        QueryClearStatus3205ACommand safe = command == null ? new QueryClearStatus3205ACommand() : command;
        ReconContractContext ctx = reconPlatformSupport.resolveContract(safe.getContractBusNo(), safe.getContractId());
        ResultBody body = gatewayClient.invoke(
            ctx.busNo(),
            ChsInfNo.CLEAR_STATUS_QUERY_JILIN,
            ReconPlatformInputAssembler.assemble3205(safe),
            reconPlatformSupport.buildInvokeContext());
        ChsPlatformResponseSupport.requireSuccess(body, ChsInfNo.CLEAR_STATUS_QUERY_JILIN);
        return ReconPlatformOutputParser.parse3205(body);
    }
}
