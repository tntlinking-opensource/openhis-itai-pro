/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.host.remote;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@ConditionalOnProperty(name = "zxplat.chs.deployment.mode", havingValue = "remote")
class ChsGatewayRemoteSupportConfiguration {

    @Bean
    @ConditionalOnMissingBean
    ChsGatewayRemoteHttpClient chsGatewayRemoteHttpClient(
        RestClient.Builder restClientBuilder,
        ChsGatewayRemoteProperties properties) {
        return new ChsGatewayRemoteHttpClient(restClientBuilder, properties);
    }
}
