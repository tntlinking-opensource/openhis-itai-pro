/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.bridge.config;

import com.zxplat.plugin.chs.gateway.config.ChsGatewayAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** 工伤 bridge 自装配：Orchestrator + ExtensionImpl + ContextLoader。
 *
 * <p>开关：{@code zxplat.modules.work-injury.enabled=true}。</p>
 * <p>扫描 {@code com.zxplat.plugin.wi.bridge} 与 {@code com.zxplat.plugin.wi.bridge.integration.workinjury}；
 * 复用 CHS gateway 与 domain JPA 配置。</p>
 */
@Configuration
@ConditionalOnProperty(name = "zxplat.modules.work-injury.enabled", havingValue = "true", matchIfMissing = true)
@Import({ChsGatewayAutoConfiguration.class, WiModuleDomainAutoConfiguration.class})
@ComponentScan(basePackages = {
    "com.zxplat.plugin.wi.bridge",
    "com.zxplat.plugin.wi.bridge.integration.workinjury"
})
public class WiBridgeAutoConfiguration {
}
