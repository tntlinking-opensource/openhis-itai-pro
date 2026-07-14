/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.config;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * CHS bridge Spring 装配入口：扫描 Invoker、ContextLoader、Extension 实现及契约适配器。
 *
 * <p>在 {@code openhis.modules.chs.enabled=true} 且 gateway 客户端可用时生效，
 * 并确保在 {@code ChsGatewayAutoConfiguration} 之后加载。</p>
 */
@Configuration
@ConditionalOnClass(name = "com.openhis.chs.gateway.client.InsuranceGatewayClient")
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
@AutoConfigureAfter(name = "com.openhis.chs.gateway.config.ChsGatewayAutoConfiguration")
@Import(ChsModuleDomainAutoConfiguration.class)
@ComponentScan(basePackages = {
    "com.openhis.chs.bridge",
    "com.openhis.health.integration.insurance"
})
public class ChsBridgeAutoConfiguration {
}
