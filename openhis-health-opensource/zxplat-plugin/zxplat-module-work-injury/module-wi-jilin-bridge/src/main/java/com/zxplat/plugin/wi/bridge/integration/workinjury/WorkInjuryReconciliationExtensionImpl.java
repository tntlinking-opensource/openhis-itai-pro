/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.bridge.integration.workinjury;

import com.zxplat.domain.infrastructure.integration.workinjury.ExtensionOut.PrepareWorkInjuryInvokeExtensionOut;
import com.zxplat.domain.infrastructure.integration.workinjury.WorkInjuryReconciliationExtension;
import com.zxplat.plugin.wi.bridge.invoke.workflow.WorkInjuryInvokeOrchestrator;
import com.zxplat.plugin.wi.domain.invoke.command.PrepareWorkInjuryInvokeRequest;
import com.zxplat.plugin.wi.gateway.scheme.shared.model.WiPhase;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 吉林工伤对账 SPI 实现。
 *
 * <p><b>SPI</b>：{@link WorkInjuryReconciliationExtension}</p>
 * <p><b>Health 调用方</b>：{@code ChsReconciliationAppService}（工伤 Tab）</p>
 * <p><b>Bridge 委托</b>：{@link WorkInjuryInvokeOrchestrator}（phase=RECON，1320→1322）</p>
 */
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnProperty(name = "zxplat.modules.work-injury.enabled", havingValue = "true", matchIfMissing = true)
public class WorkInjuryReconciliationExtensionImpl implements WorkInjuryReconciliationExtension {

    private final WorkInjuryInvokeOrchestrator workInjuryInvokeOrchestrator;

    /** 工伤模块启用时对账 Tab 可见。 */
    @Override
    public boolean isReconciliationEnabled() {
        return true;
    }

    /** 【1320】启动 RECON invoke 首步 prepare（formContext 传对账月份等）。
     *
     * @param contractId  工伤费用性质 id
     * @param operatorId  操作员 id
     * @param formContext stmt_begindate / month / socialPoolCode 等
     * @return prepare 响应（phase=RECON，nextAction=INVOKE_WIN）
     */
    @Override
    public PrepareWorkInjuryInvokeExtensionOut startRecon(
            Long contractId,
            Long operatorId,
            Map<String, Object> formContext) {
        PrepareWorkInjuryInvokeRequest request = new PrepareWorkInjuryInvokeRequest()
                .setPhase(WiPhase.RECON.getCode())
                .setContractId(contractId)
                .setOperatorId(operatorId)
                .setContext(formContext);
        return WorkInjuryIntegrationMapper.toNeutralPrepareResult(workInjuryInvokeOrchestrator.prepareInvoke(request));
    }
}
