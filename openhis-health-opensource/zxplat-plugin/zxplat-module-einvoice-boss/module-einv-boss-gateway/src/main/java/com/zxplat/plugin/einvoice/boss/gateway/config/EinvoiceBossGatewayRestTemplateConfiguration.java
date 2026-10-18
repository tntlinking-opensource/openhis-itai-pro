/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.gateway.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * 博思网关 HTTP RestTemplate 配置。
 */
@Configuration
public class EinvoiceBossGatewayRestTemplateConfiguration {

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(30);

    @Bean
    public RestTemplate bossGatewayRestTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(DEFAULT_CONNECT_TIMEOUT)
                .readTimeout(DEFAULT_READ_TIMEOUT)
                .build();
    }
}
