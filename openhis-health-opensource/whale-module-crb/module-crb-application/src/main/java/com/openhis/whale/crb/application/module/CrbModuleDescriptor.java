/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.application.module;

import com.openhis.whale.core.module.WhaleModuleDescriptor;
import org.springframework.stereotype.Component;

/**
 * CRB 模块描述符。
 */
@Component
public class CrbModuleDescriptor implements WhaleModuleDescriptor {

    @Override
    public String moduleId() {
        return "crb";
    }

    @Override
    public String displayName() {
        return "Whale CRB";
    }

    @Override
    public String enabledProperty() {
        return "openhis.modules.crb.enabled";
    }

    @Override
    public String[] dependsOnModuleIds() {
        return new String[] {"crb-bridge"};
    }

}
