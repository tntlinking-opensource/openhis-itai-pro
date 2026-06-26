/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.mrms.application.config;

import com.openhis.whale.core.module.IntegrationMode;
import com.openhis.whale.core.module.WhaleModuleDescriptor;
import org.springframework.stereotype.Component;

/**
 * MRMS 扩展模块描述符，供框架模块注册与启动校验使用。
 */
@Component
public class MrmsModuleDescriptor implements WhaleModuleDescriptor {

    /**
     * {@inheritDoc}
     */
    @Override
    public String moduleId() {
        return "mrms";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String displayName() {
        return "Whale MRMS";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String enabledProperty() {
        return "openhis.modules.mrms.enabled";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String[] dependsOnModuleIds() {
        return new String[] {"mrms-adapter"};
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IntegrationMode integrationMode() {
        return IntegrationMode.PORT;
    }
}
