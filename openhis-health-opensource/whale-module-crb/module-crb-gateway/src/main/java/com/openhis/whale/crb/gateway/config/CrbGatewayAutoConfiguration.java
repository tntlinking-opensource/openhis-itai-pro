/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.gateway.config;

import com.openhis.whale.crb.gateway.client.NationalFrontSoftwareClient;
import com.openhis.whale.crb.domain.config.CrbProperties;
import com.openhis.whale.crb.gateway.mock.MockNationalFrontSoftwareClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * CRB gateway 自动配置。
 */
@Configuration
@ComponentScan(basePackages = "com.openhis.whale.crb.gateway")
public class CrbGatewayAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(NationalFrontSoftwareClient.class)
    @ConditionalOnProperty(name = "openhis.crb.push.mock-enabled", havingValue = "true", matchIfMissing = true)
    public NationalFrontSoftwareClient mockNationalFrontSoftwareClient(CrbProperties properties) {
        return new MockNationalFrontSoftwareClient(properties);
    }
}
