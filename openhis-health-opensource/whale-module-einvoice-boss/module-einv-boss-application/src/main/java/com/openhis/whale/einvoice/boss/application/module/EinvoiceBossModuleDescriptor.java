/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.application.module;

import com.openhis.whale.core.module.WhaleModuleDescriptor;
import org.springframework.stereotype.Component;

/**
 * 博思电子票据模块描述符。
 *
 * <p>依赖方向：{@code einvoice-boss-bridge} 通过 EVENT 推送 HIS 事件到本模块；
 * application 层不反向依赖 adapter，避免循环加载误解。</p>
 */
@Component
public class EinvoiceBossModuleDescriptor implements WhaleModuleDescriptor {

    @Override
    public String moduleId() {
        return "einvoice-boss";
    }

    @Override
    public String displayName() {
        return "Whale EInvoice Boss";
    }

    @Override
    public String enabledProperty() {
        return "openhis.modules.einvoice-boss.enabled";
    }

    @Override
    public String[] dependsOnModuleIds() {
        return new String[0];
    }

}
