/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.reconciliation.invoker;

import com.zxplat.plugin.chs.domain.reconciliation.command.ReconDetail3202Command;
import com.zxplat.plugin.chs.gateway.client.InsuranceGatewayClient;
import com.zxplat.plugin.chs.gateway.client.InvokeContext;
import com.zxplat.plugin.chs.gateway.client.ResultBody;
import com.zxplat.plugin.chs.domain.share.constants.ChsInfNo;
import com.zxplat.plugin.chs.gateway.scheme.reconciliation.assembler.ReconPlatformInputAssembler;
import com.zxplat.plugin.chs.gateway.support.ChsPlatformResponseSupport;
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
