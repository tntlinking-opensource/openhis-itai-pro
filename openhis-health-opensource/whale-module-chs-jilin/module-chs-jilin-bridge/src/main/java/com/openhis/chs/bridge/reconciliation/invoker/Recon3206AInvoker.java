/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.reconciliation.invoker;

import com.openhis.chs.bridge.reconciliation.support.ReconPlatformSupport;
import com.openhis.chs.bridge.reconciliation.support.ReconPlatformSupport.ReconContractContext;
import com.openhis.chs.domain.reconciliation.command.QueryClearOrg3206ACommand;
import com.openhis.chs.domain.reconciliation.result.ClearOrg3206AResult;
import com.openhis.chs.gateway.client.InsuranceGatewayClient;
import com.openhis.chs.gateway.client.ResultBody;
import com.openhis.chs.domain.share.constants.ChsInfNo;
import com.openhis.chs.gateway.scheme.reconciliation.assembler.ReconPlatformInputAssembler;
import com.openhis.chs.gateway.scheme.reconciliation.parser.ReconPlatformOutputParser;
import com.openhis.chs.gateway.support.ChsPlatformResponseSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 【3206A】清算机构分页查询 Invoker：gateway 出站（只读，不落库）。
 * <p>实现 domain {@link com.openhis.chs.domain.reconciliation.access.Recon3206AInvoker}。</p>
 */
@Component
@RequiredArgsConstructor
public class Recon3206AInvoker implements com.openhis.chs.domain.reconciliation.access.Recon3206AInvoker {

    private final InsuranceGatewayClient gatewayClient;
    private final ReconPlatformSupport reconPlatformSupport;

    @Override
    public ClearOrg3206AResult invoke(QueryClearOrg3206ACommand command) {
        QueryClearOrg3206ACommand safe = command == null ? new QueryClearOrg3206ACommand() : command;
        ReconContractContext ctx = reconPlatformSupport.resolveContract(safe.getContractBusNo(), safe.getContractId());
        ResultBody body = gatewayClient.invoke(
            ctx.busNo(),
            ChsInfNo.CLEAR_ORG_QUERY_JILIN,
            ReconPlatformInputAssembler.assemble3206(safe, ctx.fixmedinsCode()),
            reconPlatformSupport.buildInvokeContext());
        ChsPlatformResponseSupport.requireSuccess(body, ChsInfNo.CLEAR_ORG_QUERY_JILIN);
        return ReconPlatformOutputParser.parse3206(body);
    }
}
