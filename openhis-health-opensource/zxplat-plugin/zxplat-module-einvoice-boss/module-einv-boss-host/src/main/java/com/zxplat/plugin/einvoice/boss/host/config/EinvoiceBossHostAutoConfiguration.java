/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.host.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * 博思 HTTP 中转 starter 自动配置。
 */
@Configuration
@ConditionalOnProperty(name = "zxplat.modules.einvoice-boss.http.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(EinvoiceBossHostProperties.class)
@ComponentScan(basePackages = "com.zxplat.plugin.einvoice.boss.host")
public class EinvoiceBossHostAutoConfiguration {
}
