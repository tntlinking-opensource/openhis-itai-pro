/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.bridge.module;

import com.zxplat.framework.core.module.ZxplatModuleDescriptor;
import org.springframework.stereotype.Component;

/**
 * 博思电子票据 adapter 模块描述符。
 *
 * <p>通过 EVENT 模式订阅 HIS 开票/作废事件，并调用 {@code einvoice-boss} application 提供的 Client。</p>
 */
@Component
public class EinvoiceBossBridgeModuleDescriptor implements ZxplatModuleDescriptor {

    @Override
    public String moduleId() {
        return "einvoice-boss-bridge";
    }

    @Override
    public String displayName() {
        return "HIS-EInvoice Adapter";
    }

    @Override
    public String enabledProperty() {
        return "zxplat.modules.einvoice-boss.enabled";
    }

    @Override
    public String[] dependsOnModuleIds() {
        return new String[] {"einvoice-boss"};
    }

}
