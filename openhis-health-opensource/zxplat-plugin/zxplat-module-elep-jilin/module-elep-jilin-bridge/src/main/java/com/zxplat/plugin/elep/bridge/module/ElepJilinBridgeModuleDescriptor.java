/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.elep.bridge.module;

import com.zxplat.framework.core.module.ZxplatModuleDescriptor;
import org.springframework.stereotype.Component;

@Component
public class ElepJilinBridgeModuleDescriptor implements ZxplatModuleDescriptor {

    @Override
    public String moduleId() {
        return "elep-jilin-bridge";
    }

    @Override
    public String displayName() {
        return "HIS-ELEP Adapter";
    }

    @Override
    public String enabledProperty() {
        return "zxplat.modules.elep.enabled";
    }

    @Override
    public String[] dependsOnModuleIds() {
        return new String[] {"elep"};
    }


    @Override
    public String[] featureKeys() {
        return new String[] {
                "elep.prescription-extension",
                "elep.catalog-search",
                "elep.gateway"
        };
    }
}
