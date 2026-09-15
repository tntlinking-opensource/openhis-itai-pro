/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.miniapp.application.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
@ConditionalOnProperty(name = "openhis.modules.miniapp.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(MiniappProperties.class)
@ComponentScan({"com.openhis.miniapp.application", "com.openhis.miniapp.domain"})
@EntityScan("com.openhis.miniapp.domain")
@EnableJpaRepositories("com.openhis.miniapp.domain")
public class MiniappAutoConfiguration {

    @Bean(name = "miniappRestTemplate")
    @ConditionalOnMissingBean(name = "miniappRestTemplate")
    public RestTemplate miniappRestTemplate(RestTemplateBuilder builder) {
        return builder
            .setConnectTimeout(Duration.ofSeconds(5))
            .setReadTimeout(Duration.ofSeconds(10))
            .build();
    }
}
