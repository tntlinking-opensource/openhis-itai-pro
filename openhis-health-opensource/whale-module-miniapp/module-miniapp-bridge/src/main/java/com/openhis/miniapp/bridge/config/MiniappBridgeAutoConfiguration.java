/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.miniapp.bridge.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "openhis.modules.miniapp.enabled", havingValue = "true", matchIfMissing = true)
@ComponentScan("com.openhis.miniapp.bridge")
public class MiniappBridgeAutoConfiguration {
}
