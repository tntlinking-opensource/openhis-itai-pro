/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.integration.workinjury;


import com.openhis.health.infrastructure.integration.workinjury.WorkInjuryInvokeExtension;
import com.openhis.wi.domain.invoke.access.PrepareWorkInjuryInvokeInvoker;
import com.openhis.wi.domain.invoke.access.SubmitWorkInjuryInvokeInvoker;
import com.openhis.health.infrastructure.integration.workinjury.ExtensionIn.WorkInjuryPrepareInvokeExtensionIn;
import com.openhis.health.infrastructure.integration.workinjury.ExtensionIn.WorkInjurySubmitInvokeResultExtensionIn;
import com.openhis.health.infrastructure.integration.workinjury.ExtensionOut.PrepareWorkInjuryInvokeExtensionOut;
import com.openhis.health.infrastructure.integration.workinjury.ExtensionOut.WorkInjurySubmitInvokeResultExtensionOut;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 吉林工伤 Win invoke SPI 实现。
 *
 * <p><b>SPI</b>：{@link WorkInjuryInvokeExtension}</p>
 * <p><b>Health 调用方</b>：嵌入门诊流程（经 Extension 注入）</p>
 * <p><b>Bridge 委托</b>：domain invoke access Invoker、{@link WorkInjuryIntegrationMapper}</p>
 */
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.work-injury.enabled", havingValue = "true")
public class WorkInjuryInvokeExtensionImpl implements WorkInjuryInvokeExtension {

    private final PrepareWorkInjuryInvokeInvoker prepareWorkInjuryInvokeInvoker;
    private final SubmitWorkInjuryInvokeInvoker submitWorkInjuryInvokeInvoker;

    /** prepareInvoke：下发 infno + input，nextAction=INVOKE_WIN。 */
    @Override
    public PrepareWorkInjuryInvokeExtensionOut prepareInvoke(WorkInjuryPrepareInvokeExtensionIn command) {
        return WorkInjuryIntegrationMapper.toNeutralPrepareResult(
                prepareWorkInjuryInvokeInvoker.invoke(WorkInjuryIntegrationMapper.toPrepareRequest(command)));
    }

    /** submitInvokeResult：写审计、推进 step、执行 infno 副作用。 */
    @Override
    public WorkInjurySubmitInvokeResultExtensionOut submitInvokeResult(WorkInjurySubmitInvokeResultExtensionIn command) {
        return WorkInjuryIntegrationMapper.toNeutralSubmitResponse(
                submitWorkInjuryInvokeInvoker.invoke(WorkInjuryIntegrationMapper.toSubmitRequest(command)));
    }
}
