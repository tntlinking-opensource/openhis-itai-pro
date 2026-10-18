/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.bridge.integration.workinjury;

import com.zxplat.plugin.wi.gateway.scheme.shared.model.WiBusConstants;
import com.zxplat.plugin.wi.bridge.context.WorkInjuryContractResolver;
import com.zxplat.domain.infrastructure.integration.workinjury.WorkInjuryModuleExtension;
import com.zxplat.domain.infrastructure.integration.workinjury.ExtensionOut.WorkInjuryCapabilityExtensionOut;
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
@ConditionalOnProperty(name = "zxplat.modules.work-injury.enabled", havingValue = "true", matchIfMissing = true)
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
