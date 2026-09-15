/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.miniapp.application.module;

import com.openhis.whale.core.module.WhaleModuleDescriptor;
import org.springframework.stereotype.Component;

@Component
public class MiniappModuleDescriptor implements WhaleModuleDescriptor {

    @Override
    public String moduleId() {
        return "miniapp";
    }

    @Override
    public String displayName() {
        return "Whale Miniapp";
    }

    @Override
    public String enabledProperty() {
        return "openhis.modules.miniapp.enabled";
    }

    @Override
    public String[] dependsOnModuleIds() {
        return new String[] {"miniapp-bridge"};
    }
}
