/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.bridge.register.invoker;

import com.zxplat.plugin.wi.gateway.scheme.shared.model.WiBusConstants;
import com.zxplat.plugin.wi.domain.register.access.WorkInjuryCapabilityAccess;
import com.zxplat.plugin.wi.domain.register.result.WorkInjuryCapabilityResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 工伤模块 capability 查询端口实现。 */
@Component
@ConditionalOnProperty(name = "zxplat.modules.work-injury.enabled", havingValue = "true", matchIfMissing = true)
public class WorkInjuryCapabilityAccessImpl implements WorkInjuryCapabilityAccess {

    @Override
    public WorkInjuryCapabilityResult capability() {
        return new WorkInjuryCapabilityResult()
                .setEnabled(true)
                .setWinRequired(true)
                .setFixmedinsCode(WiBusConstants.FIXMEDINS_CODE)
                .setContractBusNo(WiBusConstants.CONTRACT_BUS_NO);
    }
}
