/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.application.config;

import com.zxplat.plugin.einvoice.boss.gateway.config.EinvoiceBossGatewayProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 博思电子票据模块自动配置（embedded 模式）。
 */
@Configuration
@ConditionalOnProperty(name = "zxplat.modules.einvoice-boss.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(EinvoiceBossGatewayProperties.class)
@EnableAsync
@EnableScheduling
@ComponentScan(basePackages = {
        "com.zxplat.plugin.einvoice.boss.application",
        "com.zxplat.plugin.einvoice.boss.domain"
})
@EntityScan(basePackages = "com.zxplat.plugin.einvoice.boss.domain")
@EnableJpaRepositories(basePackages = "com.zxplat.plugin.einvoice.boss.domain")
public class EinvoiceBossAutoConfiguration {
}
