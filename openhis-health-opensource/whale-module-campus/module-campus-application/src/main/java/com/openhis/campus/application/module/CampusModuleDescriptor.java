/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.application.module;

import com.openhis.whale.core.module.WhaleModuleDescriptor;
import org.springframework.stereotype.Component;

@Component
public class CampusModuleDescriptor implements WhaleModuleDescriptor {

    @Override
    public String moduleId() {
        return "campus";
    }

    @Override
    public String displayName() {
        return "Whale Campus";
    }

    @Override
    public String enabledProperty() {
        return "openhis.modules.campus.enabled";
    }

    @Override
    public String[] dependsOnModuleIds() {
        return new String[] {"campus-bridge"};
    }
}
