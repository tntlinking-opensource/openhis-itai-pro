/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.bridge.module;

import com.openhis.whale.core.module.WhaleModuleDescriptor;
import org.springframework.stereotype.Component;

@Component
public class CampusBridgeModuleDescriptor implements WhaleModuleDescriptor {

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
        return "openhis.modules.campus.enabled";
    }

    @Override
    public String[] dependsOnModuleIds() {
        return new String[0];
    }
}
