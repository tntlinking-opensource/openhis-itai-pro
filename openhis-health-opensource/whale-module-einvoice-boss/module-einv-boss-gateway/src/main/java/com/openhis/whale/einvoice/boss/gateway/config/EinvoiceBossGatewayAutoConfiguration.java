/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.gateway.config;

import com.openhis.whale.einvoice.boss.gateway.client.EinvoiceBossGatewayClient;
import com.openhis.whale.einvoice.boss.gateway.local.EinvoiceBossGatewayClientLocalImpl;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

/** 博思 gateway 本地出站装配。 */
@AutoConfiguration
@EnableConfigurationProperties(EinvoiceBossGatewayProperties.class)
@ComponentScan(basePackages = "com.openhis.whale.einvoice.boss.gateway.scheme")
public class EinvoiceBossGatewayAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(EinvoiceBossGatewayClient.class)
    public EinvoiceBossGatewayClient einvoiceBossGatewayClient(EinvoiceBossGatewayClientLocalImpl localImpl) {
        return localImpl;
    }
}
