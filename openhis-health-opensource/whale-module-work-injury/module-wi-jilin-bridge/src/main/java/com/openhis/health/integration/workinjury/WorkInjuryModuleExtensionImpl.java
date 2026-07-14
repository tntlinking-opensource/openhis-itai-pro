/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.integration.workinjury;

import com.openhis.wi.gateway.scheme.shared.model.WiBusConstants;
import com.openhis.wi.bridge.context.WorkInjuryContractResolver;
import com.openhis.health.infrastructure.integration.workinjury.WorkInjuryModuleExtension;
import com.openhis.health.infrastructure.integration.workinjury.ExtensionOut.WorkInjuryCapabilityExtensionOut;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 吉林工伤模块能力 SPI 实现。
 *
 * <p><b>SPI</b>：{@link WorkInjuryModuleExtension}</p>
 * <p><b>Health 调用方</b>：capability API、contract 路由</p>
 * <p><b>Bridge 委托</b>：{@link WorkInjuryContractResolver}</p>
 */
@Component
@Primary
@ConditionalOnProperty(name = "openhis.modules.work-injury.enabled", havingValue = "true")
public class WorkInjuryModuleExtensionImpl implements WorkInjuryModuleExtension {

    private final WorkInjuryContractResolver contractResolver;

    public WorkInjuryModuleExtensionImpl(WorkInjuryContractResolver contractResolver) {
        this.contractResolver = contractResolver;
    }

    /** wi-jilin-starter 装配时为 true。 */
    @Override
    public boolean isEnabled() {
        return true;
    }

    /** WORKER_INSURANCE / *-GS 契约走工伤 Extension。 */
    @Override
    public boolean supportsContract(Long contractId) {
        return contractResolver.isWorkInjuryContract(contractId);
    }

    /** 返回 fixmedins_code、bus_no、winRequired 等 capability。 */
    @Override
    public WorkInjuryCapabilityExtensionOut capability() {
        return new WorkInjuryCapabilityExtensionOut()
                .setEnabled(true)
                .setWinRequired(true)
                .setFixmedinsCode(WiBusConstants.FIXMEDINS_CODE)
                .setContractBusNo(WiBusConstants.CONTRACT_BUS_NO);
    }
}
