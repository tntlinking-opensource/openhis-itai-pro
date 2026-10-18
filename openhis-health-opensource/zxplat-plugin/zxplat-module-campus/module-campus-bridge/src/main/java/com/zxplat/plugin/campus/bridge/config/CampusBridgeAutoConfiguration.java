/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.bridge.config;

import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * campus-bridge 自动配置：扫描 bridge 与 Integration SPI 实现。
 */
@Configuration
@ConditionalOnProperty(name = "zxplat.modules.campus.enabled", havingValue = "true", matchIfMissing = true)
@AutoConfigureBefore(name = "com.zxplat.plugin.campus.application.config.CampusAutoConfiguration")
@ComponentScan(basePackages = {
        "com.zxplat.plugin.campus.bridge",
        "com.zxplat.plugin.campus.bridge.integration"
})
public class CampusBridgeAutoConfiguration {
}
