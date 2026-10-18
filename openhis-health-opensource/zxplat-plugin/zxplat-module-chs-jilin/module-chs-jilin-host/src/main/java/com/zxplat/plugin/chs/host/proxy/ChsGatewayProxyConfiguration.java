/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.host.proxy;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/** gateway-proxy Controller 装配。 */
@Configuration
@ConditionalOnProperty(name = "zxplat.chs.gateway.proxy.standalone", havingValue = "true")
@ComponentScan(basePackageClasses = ChsGatewayProxyController.class)
public class ChsGatewayProxyConfiguration {
}
