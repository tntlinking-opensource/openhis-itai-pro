/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.elep.bridge.config;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(name = "com.zxplat.plugin.elep.gateway.client.ElepGatewayClient")
@ConditionalOnProperty(name = "zxplat.modules.elep.enabled", havingValue = "true", matchIfMissing = true)
@AutoConfigureAfter(name = "com.zxplat.plugin.elep.application.config.ElepAutoConfiguration")
@ComponentScan(basePackages = {
        "com.zxplat.plugin.elep.bridge"
})
public class ElepJilinBridgeAutoConfiguration {
}
