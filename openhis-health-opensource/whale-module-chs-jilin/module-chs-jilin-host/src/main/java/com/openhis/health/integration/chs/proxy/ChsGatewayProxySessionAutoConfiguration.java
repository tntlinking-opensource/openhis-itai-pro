/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.integration.chs.proxy;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** gateway-proxy 侧 {@code yb_pub_sign} 等 session 持久化（不含 bridge）。 */
@Configuration
@ConditionalOnProperty(name = "openhis.chs.gateway.proxy.standalone", havingValue = "true")
@ComponentScan(basePackages = "com.openhis.chs.domain.session")
@EntityScan(basePackages = "com.openhis.chs.domain.session.entity")
@EnableJpaRepositories(basePackages = "com.openhis.chs.domain.session.repository")
public class ChsGatewayProxySessionAutoConfiguration {
}
