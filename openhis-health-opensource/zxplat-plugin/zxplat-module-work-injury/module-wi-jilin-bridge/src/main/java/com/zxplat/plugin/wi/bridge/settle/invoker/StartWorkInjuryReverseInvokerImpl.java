/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.bridge.settle.invoker;

import com.zxplat.plugin.wi.bridge.settle.workflow.WorkInjuryOutpatientSettleOrchestrator;
import com.zxplat.plugin.wi.domain.invoke.result.PrepareWorkInjuryInvokeResponse;
import com.zxplat.plugin.wi.domain.settle.access.StartWorkInjuryReverseInvoker;
import com.zxplat.plugin.wi.domain.settle.command.StartWorkInjuryReverseCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 2208 退费 invoke 首步 prepare 端口实现。 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "zxplat.modules.work-injury.enabled", havingValue = "true", matchIfMissing = true)
public class StartWorkInjuryReverseInvokerImpl implements StartWorkInjuryReverseInvoker {

    private final WorkInjuryOutpatientSettleOrchestrator workInjuryOutpatientSettleOrchestrator;

    @Override
    public PrepareWorkInjuryInvokeResponse invoke(StartWorkInjuryReverseCommand command) {
        return workInjuryOutpatientSettleOrchestrator.startReverse(command);
    }
}
