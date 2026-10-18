/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.miniapp.bridge.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * miniapp-bridge 自动配置：扫描 bridge 与 integration（预留 ExtensionImpl）。
 */
@Configuration
@ConditionalOnProperty(name = "zxplat.modules.miniapp.enabled", havingValue = "true", matchIfMissing = true)
@ComponentScan(basePackages = {
        "com.zxplat.plugin.miniapp.bridge",
        "com.zxplat.plugin.miniapp.bridge.integration"
})
public class MiniappBridgeAutoConfiguration {
}
