/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.emr.bridge.module;

import com.openhis.whale.core.module.WhaleModuleDescriptor;
import org.springframework.stereotype.Component;

@Component
public class EmrBridgeModuleDescriptor implements WhaleModuleDescriptor {

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
        return "openhis.modules.emr.enabled";
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
