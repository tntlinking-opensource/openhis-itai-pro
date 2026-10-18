/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.unionpay.bpc.application.module;

import com.zxplat.framework.core.module.ZxplatModuleDescriptor;
import org.springframework.stereotype.Component;

/**
 * 银联 BPC 模块描述符。
 */
@Component
public class UnionPayBpcModuleDescriptor implements ZxplatModuleDescriptor {

    @Override
    public String moduleId() {
        return "unionpay-bpc";
    }

    @Override
    public String displayName() {
        return "ZXPlat UnionPay BPC";
    }

    @Override
    public String enabledProperty() {
        return "zxplat.modules.unionpay-bpc.enabled";
    }

    @Override
    public String[] dependsOnModuleIds() {
        return new String[] {"unionpay-bpc-bridge"};
    }

}
