/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.application.module;

import com.zxplat.framework.core.module.ZxplatModuleDescriptor;
import org.springframework.stereotype.Component;

/**
 * 博思电子票据模块描述符。
 *
 * <p>依赖方向：{@code einvoice-boss-bridge} 通过 EVENT 推送 HIS 事件到本模块；
 * application 层不反向依赖 adapter，避免循环加载误解。</p>
 */
@Component
public class EinvoiceBossModuleDescriptor implements ZxplatModuleDescriptor {

    @Override
    public String moduleId() {
        return "einvoice-boss";
    }

    @Override
    public String displayName() {
        return "ZXPlat EInvoice Boss";
    }

    @Override
    public String enabledProperty() {
        return "zxplat.modules.einvoice-boss.enabled";
    }

    @Override
    public String[] dependsOnModuleIds() {
        return new String[0];
    }

}
