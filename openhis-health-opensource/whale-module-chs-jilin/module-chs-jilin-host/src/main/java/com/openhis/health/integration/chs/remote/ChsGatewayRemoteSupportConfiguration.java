/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.integration.chs.remote;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@ConditionalOnProperty(name = "openhis.chs.deployment.mode", havingValue = "remote")
class ChsGatewayRemoteSupportConfiguration {

    @Bean
    @ConditionalOnMissingBean
    ChsGatewayRemoteHttpClient chsGatewayRemoteHttpClient(
        RestClient.Builder restClientBuilder,
        ChsGatewayRemoteProperties properties) {
        return new ChsGatewayRemoteHttpClient(restClientBuilder, properties);
    }
}
