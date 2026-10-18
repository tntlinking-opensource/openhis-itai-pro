/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.host.remote;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

/**
 * HIS 侧 remote 装配：{@code zxplat.chs.deployment.mode=remote} 时注册 {@link InsuranceGatewayClientRemoteImpl}。
 */
@AutoConfiguration
@ConditionalOnProperty(name = "zxplat.chs.deployment.mode", havingValue = "remote")
@EnableConfigurationProperties(ChsGatewayRemoteProperties.class)
@Import({ChsGatewayRemoteSupportConfiguration.class, ChsGatewayRemoteClientConfiguration.class})
@ComponentScan(basePackageClasses = InsuranceGatewayClientRemoteImpl.class)
public class ChsGatewayRemoteAutoConfiguration {
}
