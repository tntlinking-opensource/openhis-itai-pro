/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.config;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * CHS bridge Spring 装配入口：扫描 Invoker、ContextLoader、Extension 实现及契约适配器。
 *
 * <p>在 {@code zxplat.modules.chs.enabled=true} 且 gateway 客户端可用时生效，
 * 并确保在 {@code ChsGatewayAutoConfiguration} 之后加载。</p>
 */
@Configuration
@ConditionalOnClass(name = "com.zxplat.plugin.chs.gateway.client.InsuranceGatewayClient")
@ConditionalOnProperty(name = "zxplat.modules.chs.enabled", havingValue = "true", matchIfMissing = true)
@AutoConfigureAfter(name = "com.zxplat.plugin.chs.gateway.config.ChsGatewayAutoConfiguration")
@Import(ChsModuleDomainAutoConfiguration.class)
@ComponentScan(basePackages = {
    "com.zxplat.plugin.chs.bridge",
    "com.zxplat.plugin.chs.bridge.integration.insurance"
})
public class ChsBridgeAutoConfiguration {
}
