/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.reconciliation.invoker;

import com.openhis.chs.domain.reconciliation.command.ReconDetail3202Command;
import com.openhis.chs.gateway.client.InsuranceGatewayClient;
import com.openhis.chs.gateway.client.InvokeContext;
import com.openhis.chs.gateway.client.ResultBody;
import com.openhis.chs.domain.share.constants.ChsInfNo;
import com.openhis.chs.gateway.scheme.reconciliation.assembler.ReconPlatformInputAssembler;
import com.openhis.chs.gateway.support.ChsPlatformResponseSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 【3202】明细对账提交 gateway 出站（不落库，由 3202 编排 saveReconDetail）。 */
@Component
@RequiredArgsConstructor
public class Recon3202Invoker {

    private final InsuranceGatewayClient gatewayClient;

    public ResultBody invoke(String contractBusNo, ReconDetail3202Command command, InvokeContext invokeContext) {
        ResultBody body = gatewayClient.invoke(
            contractBusNo,
            ChsInfNo.RECON_DETAIL_LEDGER,
            ReconPlatformInputAssembler.assemble3202(command),
            invokeContext);
        ChsPlatformResponseSupport.requireSuccess(body, ChsInfNo.RECON_DETAIL_LEDGER);
        return body;
    }
}
