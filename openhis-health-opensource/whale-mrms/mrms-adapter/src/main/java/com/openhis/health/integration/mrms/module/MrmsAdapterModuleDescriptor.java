/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.mrms.module;

import com.openhis.whale.core.module.IntegrationMode;
import com.openhis.whale.core.module.WhaleModuleDescriptor;
import org.springframework.stereotype.Component;

/**
 * MRMS adapter 模块描述符（离院建档 Extension 等 HIS 侧挂接）。
 */
@Component
public class MrmsAdapterModuleDescriptor implements WhaleModuleDescriptor {

    @Override
    public String moduleId() {
        return "mrms-adapter";
    }

    @Override
    public String displayName() {
        return "HIS-MRMS Adapter";
    }

    @Override
    public String enabledProperty() {
        return "openhis.modules.mrms.enabled";
    }

    @Override
    public String[] dependsOnModuleIds() {
        return new String[] {"mrms"};
    }

    @Override
    public IntegrationMode integrationMode() {
        return IntegrationMode.PUSH;
    }

    @Override
    public String[] featureKeys() {
        return new String[] {"mrms.discharge-bootstrap"};
    }
}
