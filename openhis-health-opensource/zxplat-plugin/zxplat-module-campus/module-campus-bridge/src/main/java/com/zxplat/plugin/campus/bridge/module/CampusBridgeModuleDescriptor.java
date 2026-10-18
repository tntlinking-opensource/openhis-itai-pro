/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.bridge.module;

import com.zxplat.framework.core.module.ZxplatModuleDescriptor;
import org.springframework.stereotype.Component;

@Component
public class CampusBridgeModuleDescriptor implements ZxplatModuleDescriptor {

    @Override
    public String moduleId() {
        return "campus-bridge";
    }

    @Override
    public String displayName() {
        return "Campus Bridge";
    }

    @Override
    public String enabledProperty() {
        return "zxplat.modules.campus.enabled";
    }

    @Override
    public String[] dependsOnModuleIds() {
        return new String[0];
    }
}
