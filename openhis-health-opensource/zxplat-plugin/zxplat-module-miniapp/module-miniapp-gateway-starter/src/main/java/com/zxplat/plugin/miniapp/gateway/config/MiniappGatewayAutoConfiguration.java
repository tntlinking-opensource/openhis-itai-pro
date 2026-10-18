/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.miniapp.gateway.config;

import java.time.Duration;

import com.zxplat.plugin.miniapp.gateway.proxy.MiniappGatewayProxyController;
import com.zxplat.plugin.miniapp.gateway.proxy.MiniappGatewayProxyService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@ConditionalOnProperty(name = "zxplat.miniapp.gateway.standalone", havingValue = "true")
@EnableConfigurationProperties(MiniappGatewayProperties.class)
@ComponentScan(basePackageClasses = {MiniappGatewayProxyController.class, MiniappGatewayProxyService.class})
public class MiniappGatewayAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "miniappGatewayRestClient")
    public RestClient miniappGatewayRestClient(RestClient.Builder builder, MiniappGatewayProperties properties) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
            .withConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
            .withReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));
        return builder
            .requestFactory(ClientHttpRequestFactories.get(settings))
            .build();
    }
}
