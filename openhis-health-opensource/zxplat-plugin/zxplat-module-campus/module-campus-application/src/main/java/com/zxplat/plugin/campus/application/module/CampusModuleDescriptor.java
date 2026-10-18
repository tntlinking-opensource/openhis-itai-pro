/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.application.module;

import com.zxplat.framework.core.module.ZxplatModuleDescriptor;
import org.springframework.stereotype.Component;

@Component
public class CampusModuleDescriptor implements ZxplatModuleDescriptor {

    @Override
    public String moduleId() {
        return "campus";
    }

    @Override
    public String displayName() {
        return "ZXPlat Campus";
    }

    @Override
    public String enabledProperty() {
        return "zxplat.modules.campus.enabled";
    }

    @Override
    public String[] dependsOnModuleIds() {
        return new String[] {"campus-bridge"};
    }
}
