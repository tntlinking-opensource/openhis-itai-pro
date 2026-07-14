/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * CHS 外平台传输层装配（Gateway-Only Remote 样板）。
 *
 * <p>local：{@code InsuranceGatewayClientLocalImpl} + HTTP 出站；remote 由 host 提供 RemoteImpl。<。</p> */
@Configuration
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
@Import(InsuranceGatewayClientConfiguration.class)
@ComponentScan(basePackages = {
    "com.openhis.chs.gateway.catalog",
    "com.openhis.chs.gateway.config",
    "com.openhis.chs.gateway.crypto",
    "com.openhis.chs.gateway.http",
    "com.openhis.chs.gateway.local",
    "com.openhis.chs.gateway.scheme",
    "com.openhis.chs.gateway.session",
    "com.openhis.chs.gateway.support"
})
public class ChsGatewayAutoConfiguration {
}
