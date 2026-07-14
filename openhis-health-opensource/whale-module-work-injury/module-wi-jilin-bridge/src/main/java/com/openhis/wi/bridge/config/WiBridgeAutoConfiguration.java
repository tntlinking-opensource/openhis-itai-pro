/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.bridge.config;

import com.openhis.chs.gateway.config.ChsGatewayAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** 工伤 bridge 自装配：Orchestrator + ExtensionImpl + ContextLoader。
 *
 * <p>开关：{@code openhis.modules.work-injury.enabled=true}。</p>
 * <p>扫描 {@code com.openhis.wi.bridge} 与 {@code com.openhis.health.integration.workinjury}；
 * 复用 CHS gateway 与 domain JPA 配置。</p>
 */
@Configuration
@ConditionalOnProperty(name = "openhis.modules.work-injury.enabled", havingValue = "true")
@Import({ChsGatewayAutoConfiguration.class, WiModuleDomainAutoConfiguration.class})
@ComponentScan(basePackages = {
    "com.openhis.wi.bridge",
    "com.openhis.health.integration.workinjury"
})
public class WiBridgeAutoConfiguration {
}
