/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.lis.bridge.config;

import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "zxplat.modules.lis.enabled", havingValue = "true", matchIfMissing = true)
@AutoConfigureBefore(name = "com.zxplat.plugin.lis.application.config.LisAutoConfiguration")
@ComponentScan(basePackages = "com.zxplat.plugin.lis.bridge")
public class LisBridgeAutoConfiguration {
}