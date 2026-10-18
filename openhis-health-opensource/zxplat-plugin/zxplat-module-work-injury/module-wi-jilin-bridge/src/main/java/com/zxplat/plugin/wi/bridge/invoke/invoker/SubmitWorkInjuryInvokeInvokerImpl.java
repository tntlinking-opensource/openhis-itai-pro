/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.bridge.invoke.invoker;

import com.zxplat.plugin.wi.bridge.invoke.workflow.WorkInjuryInvokeOrchestrator;
import com.zxplat.plugin.wi.domain.invoke.access.SubmitWorkInjuryInvokeInvoker;
import com.zxplat.plugin.wi.domain.invoke.command.SubmitWorkInjuryInvokeResultRequest;
import com.zxplat.plugin.wi.domain.invoke.result.SubmitWorkInjuryInvokeResultResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Win invoke submit Invoker。
 *
 * <p>实现 domain {@link SubmitWorkInjuryInvokeInvoker}；委托 {@link WorkInjuryInvokeOrchestrator}。</p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "zxplat.modules.work-injury.enabled", havingValue = "true", matchIfMissing = true)
public class SubmitWorkInjuryInvokeInvokerImpl implements SubmitWorkInjuryInvokeInvoker {

    private final WorkInjuryInvokeOrchestrator workInjuryInvokeOrchestrator;

    @Override
    public SubmitWorkInjuryInvokeResultResponse invoke(SubmitWorkInjuryInvokeResultRequest request) {
        return workInjuryInvokeOrchestrator.submitInvokeResult(request);
    }
}
