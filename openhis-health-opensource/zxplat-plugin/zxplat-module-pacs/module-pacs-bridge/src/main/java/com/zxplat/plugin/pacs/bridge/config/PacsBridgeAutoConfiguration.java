/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.bridge.config;

import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "zxplat.modules.pacs.enabled", havingValue = "true", matchIfMissing = true)
@AutoConfigureBefore(name = "com.zxplat.plugin.pacs.application.config.PacsAutoConfiguration")
@ComponentScan(basePackages = "com.zxplat.plugin.pacs.bridge")
public class PacsBridgeAutoConfiguration {
}