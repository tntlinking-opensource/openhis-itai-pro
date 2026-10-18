/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.reconciliation.invoker;

import com.zxplat.plugin.chs.bridge.reconciliation.support.ReconPlatformSupport;
import com.zxplat.plugin.chs.bridge.reconciliation.support.ReconPlatformSupport.ReconContractContext;
import com.zxplat.plugin.chs.domain.reconciliation.command.RevokeJilinClear3204ACommand;
import com.zxplat.plugin.chs.domain.reconciliation.service.YbReconciliationDomainService;
import com.zxplat.plugin.chs.gateway.client.InsuranceGatewayClient;
import com.zxplat.plugin.chs.gateway.client.ResultBody;
import com.zxplat.plugin.chs.domain.share.constants.ChsInfNo;
import com.zxplat.plugin.chs.gateway.scheme.reconciliation.assembler.ReconPlatformInputAssembler;
import com.zxplat.plugin.chs.gateway.support.ChsPlatformResponseSupport;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 【3204A】吉林清算申请撤销 Invoker：gateway 出站；成功后 markClearApplyRevoked 更新本地状态。
 * <p>实现 domain {@link com.zxplat.plugin.chs.domain.reconciliation.access.Recon3204AInvoker}。</p>
 */
@Component
@RequiredArgsConstructor
public class Recon3204AInvoker implements com.zxplat.plugin.chs.domain.reconciliation.access.Recon3204AInvoker {

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
