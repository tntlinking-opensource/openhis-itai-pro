/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.bridge.invoke.invoker;

import com.openhis.wi.bridge.invoke.workflow.WorkInjuryInvokeOrchestrator;
import com.openhis.wi.domain.invoke.access.PrepareWorkInjuryInvokeInvoker;
import com.openhis.wi.domain.invoke.command.PrepareWorkInjuryInvokeRequest;
import com.openhis.wi.domain.invoke.result.PrepareWorkInjuryInvokeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Win invoke prepare Invoker。
 *
 * <p>实现 domain {@link PrepareWorkInjuryInvokeInvoker}；委托 {@link WorkInjuryInvokeOrchestrator}。</p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.work-injury.enabled", havingValue = "true")
public class PrepareWorkInjuryInvokeInvokerImpl implements PrepareWorkInjuryInvokeInvoker {

    private final WorkInjuryInvokeOrchestrator workInjuryInvokeOrchestrator;

    @Override
    public PrepareWorkInjuryInvokeResponse invoke(PrepareWorkInjuryInvokeRequest request) {
        return workInjuryInvokeOrchestrator.prepareInvoke(request);
    }
}
