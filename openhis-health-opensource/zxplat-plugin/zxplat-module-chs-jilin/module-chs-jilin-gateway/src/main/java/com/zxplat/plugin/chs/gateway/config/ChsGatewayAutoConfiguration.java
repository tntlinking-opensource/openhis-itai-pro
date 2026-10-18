/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * CHS 外平台传输层装配（Gateway-Only Remote 样板）。
 *
 * <p>local：{@code InsuranceGatewayClientLocalImpl} + HTTP 出站；remote 由 host 提供 RemoteImpl。<。</p> */
@Configuration
@ConditionalOnProperty(name = "zxplat.modules.chs.enabled", havingValue = "true", matchIfMissing = true)
@Import(InsuranceGatewayClientConfiguration.class)
@ComponentScan(basePackages = {
    "com.zxplat.plugin.chs.gateway.catalog",
    "com.zxplat.plugin.chs.gateway.config",
    "com.zxplat.plugin.chs.gateway.crypto",
    "com.zxplat.plugin.chs.gateway.http",
    "com.zxplat.plugin.chs.gateway.local",
    "com.zxplat.plugin.chs.gateway.scheme",
    "com.zxplat.plugin.chs.gateway.session",
    "com.zxplat.plugin.chs.gateway.support"
})
public class ChsGatewayAutoConfiguration {
}
