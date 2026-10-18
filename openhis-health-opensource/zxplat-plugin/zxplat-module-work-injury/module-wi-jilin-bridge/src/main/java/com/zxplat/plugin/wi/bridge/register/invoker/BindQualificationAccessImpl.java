/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.bridge.register.invoker;

import com.zxplat.plugin.wi.domain.register.access.BindQualificationAccess;
import com.zxplat.plugin.wi.domain.register.command.BindQualificationCommand;
import com.zxplat.plugin.wi.bridge.register.workflow.WorkInjuryRegisterOrchestrator;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 9102 后绑定 qualification 端口实现。 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "zxplat.modules.work-injury.enabled", havingValue = "true", matchIfMissing = true)
public class BindQualificationAccessImpl implements BindQualificationAccess {

    private final WorkInjuryRegisterOrchestrator workInjuryRegisterOrchestrator;

    @Override
    public void bind(BindQualificationCommand command) {
        workInjuryRegisterOrchestrator.bindQualification(command);
    }
}
