/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.bridge.module;

import com.openhis.whale.core.module.WhaleModuleDescriptor;
import org.springframework.stereotype.Component;

/**
 * CRB adapter 模块描述符。
 */
@Component
public class CrbBridgeModuleDescriptor implements WhaleModuleDescriptor {

    @Override
    public String moduleId() {
        return "crb-bridge";
    }

    @Override
    public String displayName() {
        return "HIS-CRB Adapter";
    }

    @Override
    public String enabledProperty() {
        return "openhis.modules.crb.enabled";
    }

    @Override
    public String[] dependsOnModuleIds() {
        return new String[] {"crb"};
    }

}
