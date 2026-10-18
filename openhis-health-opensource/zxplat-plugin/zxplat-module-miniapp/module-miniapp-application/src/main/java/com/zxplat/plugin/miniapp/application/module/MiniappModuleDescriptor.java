/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.miniapp.application.module;

import com.zxplat.framework.core.module.ZxplatModuleDescriptor;
import org.springframework.stereotype.Component;

@Component
public class MiniappModuleDescriptor implements ZxplatModuleDescriptor {

    @Override
    public String moduleId() {
        return "miniapp";
    }

    @Override
    public String displayName() {
        return "ZXPlat Miniapp";
    }

    @Override
    public String enabledProperty() {
        return "zxplat.modules.miniapp.enabled";
    }

    @Override
    public String[] dependsOnModuleIds() {
        return new String[] {"miniapp-bridge"};
    }
}
