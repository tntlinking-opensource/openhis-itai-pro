/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.bridge.module;

import com.zxplat.framework.core.module.ZxplatModuleDescriptor;
import org.springframework.stereotype.Component;

@Component
public class EmrBridgeModuleDescriptor implements ZxplatModuleDescriptor {

    @Override
    public String moduleId() {
        return "emr-bridge";
    }

    @Override
    public String displayName() {
        return "HIS-EMR Adapter";
    }

    @Override
    public String enabledProperty() {
        return "zxplat.modules.emr.enabled";
    }

    @Override
    public String[] dependsOnModuleIds() {
        return new String[] {"emr"};
    }


    @Override
    public String[] featureKeys() {
        return new String[] {"emr.metadata-reader"};
    }
}
