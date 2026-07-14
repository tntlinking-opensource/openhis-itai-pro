/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.reconciliation.invoker;

import com.openhis.chs.bridge.reconciliation.support.ReconPlatformSupport;
import com.openhis.chs.bridge.reconciliation.support.ReconPlatformSupport.ReconContractContext;
import com.openhis.chs.domain.reconciliation.command.RevokeJilinClear3204ACommand;
import com.openhis.chs.domain.reconciliation.service.YbReconciliationDomainService;
import com.openhis.chs.gateway.client.InsuranceGatewayClient;
import com.openhis.chs.gateway.client.ResultBody;
import com.openhis.chs.domain.share.constants.ChsInfNo;
import com.openhis.chs.gateway.scheme.reconciliation.assembler.ReconPlatformInputAssembler;
import com.openhis.chs.gateway.support.ChsPlatformResponseSupport;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 【3204A】吉林清算申请撤销 Invoker：gateway 出站；成功后 markClearApplyRevoked 更新本地状态。
 * <p>实现 domain {@link com.openhis.chs.domain.reconciliation.access.Recon3204AInvoker}。</p>
 */
@Component
@RequiredArgsConstructor
public class Recon3204AInvoker implements com.openhis.chs.domain.reconciliation.access.Recon3204AInvoker {

    private final InsuranceGatewayClient gatewayClient;
    private final ReconPlatformSupport reconPlatformSupport;
    private final YbReconciliationDomainService reconciliationDomainService;

    @Override
    public void invoke(RevokeJilinClear3204ACommand command) {
        RevokeJilinClear3204ACommand safe = command == null ? new RevokeJilinClear3204ACommand() : command;
        ReconContractContext ctx = reconPlatformSupport.resolveContract(safe.getContractBusNo(), safe.getContractId());
        ResultBody body = gatewayClient.invoke(
            ctx.busNo(),
            ChsInfNo.CLEAR_APPLY_REVOKE_JILIN,
            ReconPlatformInputAssembler.assemble3204(safe),
            reconPlatformSupport.buildInvokeContext());
        ChsPlatformResponseSupport.requireSuccess(body, ChsInfNo.CLEAR_APPLY_REVOKE_JILIN);
        reconciliationDomainService.markClearApplyRevoked(safe.getClrAppyEvtId(), TenantUtils.getCurrentTenantId());
    }
}
