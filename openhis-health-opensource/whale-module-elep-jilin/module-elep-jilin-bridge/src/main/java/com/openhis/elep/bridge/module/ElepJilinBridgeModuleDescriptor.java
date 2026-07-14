/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.elep.bridge.module;

import com.openhis.whale.core.module.WhaleModuleDescriptor;
import org.springframework.stereotype.Component;

@Component
public class ElepJilinBridgeModuleDescriptor implements WhaleModuleDescriptor {

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
        return "openhis.modules.elep.enabled";
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
