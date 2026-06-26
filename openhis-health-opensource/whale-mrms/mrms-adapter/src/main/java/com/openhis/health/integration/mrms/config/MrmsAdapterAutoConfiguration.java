/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.mrms.config;

import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * MRMS adapter 自装配：扫描 health 侧 契约实现与离院监听。
 */
@Configuration
@ConditionalOnProperty(name = "openhis.modules.mrms.enabled", havingValue = "true")
@AutoConfigureBefore(name = "com.openhis.whale.mrms.application.config.MrmsAutoConfiguration")
@ComponentScan(basePackages = "com.openhis.health.integration.mrms")
public class MrmsAdapterAutoConfiguration {
}
