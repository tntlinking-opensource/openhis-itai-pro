/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.gateway.config;

import com.zxplat.plugin.einvoice.boss.gateway.client.EinvoiceBossGatewayClient;
import com.zxplat.plugin.einvoice.boss.gateway.local.EinvoiceBossGatewayClientLocalImpl;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * 博思 gateway 本地出站装配。
 *
 * <p>院点启动类不扫描 {@code com.zxplat.plugin.*}，须在此扫全 gateway 子包
 *（Whale 时代靠 {@code com.openhis.whale} 大包扫描带上 local/config）。</p>
 */
@AutoConfiguration
@EnableConfigurationProperties(EinvoiceBossGatewayProperties.class)
@ComponentScan(
        basePackages = "com.zxplat.plugin.einvoice.boss.gateway",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = EinvoiceBossGatewayAutoConfiguration.class))
public class EinvoiceBossGatewayAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(EinvoiceBossGatewayClient.class)
    public EinvoiceBossGatewayClient einvoiceBossGatewayClient(EinvoiceBossGatewayClientLocalImpl localImpl) {
        return localImpl;
    }
}
