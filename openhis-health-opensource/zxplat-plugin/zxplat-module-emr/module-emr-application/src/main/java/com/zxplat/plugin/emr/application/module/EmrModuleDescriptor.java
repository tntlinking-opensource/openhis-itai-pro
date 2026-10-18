/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.application.module;

import com.zxplat.framework.core.module.ZxplatModuleDescriptor;
import org.springframework.stereotype.Component;

@Component
public class EmrModuleDescriptor implements ZxplatModuleDescriptor {

    @Override
    public String moduleId() {
        return "emr";
    }

    @Override
    public String displayName() {
        return "ZXPlat EMR";
    }

    @Override
    public String enabledProperty() {
        return "zxplat.modules.emr.enabled";
    }

    @Override
    public String[] dependsOnModuleIds() {
        return new String[] {"emr-bridge"};
    }

}
