/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.unionpay.bpc.bridge.module;

import com.zxplat.framework.core.module.ZxplatModuleDescriptor;
import org.springframework.stereotype.Component;

/**
 * 银联 BPC bridge 模块描述符。
 */
@Component
public class UnionPayBpcBridgeModuleDescriptor implements ZxplatModuleDescriptor {

    @Override
    public String moduleId() {
        return "unionpay-bpc-bridge";
    }

    @Override
    public String displayName() {
        return "HIS-UnionPay-BPC Bridge";
    }

    @Override
    public String enabledProperty() {
        return "zxplat.modules.unionpay-bpc.enabled";
    }

    @Override
    public String[] dependsOnModuleIds() {
        return new String[] {"unionpay-bpc"};
    }
}
