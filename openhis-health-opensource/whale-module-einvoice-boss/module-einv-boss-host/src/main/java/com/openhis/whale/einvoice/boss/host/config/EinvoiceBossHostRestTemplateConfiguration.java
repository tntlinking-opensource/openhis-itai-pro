/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.host.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * HTTP 中转 RestTemplate 配置（connect/read 超时）。
 */
@Configuration
public class EinvoiceBossHostRestTemplateConfiguration {

    /**
     * 博思转发专用 RestTemplate。
     *
     * @param properties HTTP 配置
     * @param builder Spring RestTemplateBuilder
     * @return RestTemplate
     */
    @Bean
    public RestTemplate einvoiceBossForwardRestTemplate(
            EinvoiceBossHostProperties properties, RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .readTimeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                .build();
    }
}
