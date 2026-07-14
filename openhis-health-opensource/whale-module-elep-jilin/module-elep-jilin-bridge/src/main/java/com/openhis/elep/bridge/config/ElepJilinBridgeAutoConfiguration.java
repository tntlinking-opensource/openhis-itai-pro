/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.elep.bridge.config;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(name = "com.openhis.elep.gateway.client.ElepGatewayClient")
@ConditionalOnProperty(name = "openhis.modules.elep.enabled", havingValue = "true")
@AutoConfigureAfter(name = "com.openhis.elep.application.config.ElepAutoConfiguration")
@ComponentScan(basePackages = {
        "com.openhis.elep.bridge",
        "com.openhis.health.infrastructure.integration.elep"
})
public class ElepJilinBridgeAutoConfiguration {
}
