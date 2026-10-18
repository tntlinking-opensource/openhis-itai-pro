/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.elep.host.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

/**
 * ELEP remote 客户端自装配（驻留 elep-jilin-host）。
 */
@AutoConfiguration
@ConditionalOnProperty(name = "zxplat.elep.deployment.mode", havingValue = "remote")
@EnableConfigurationProperties(ElepDeploymentRemoteProperties.class)
@ComponentScan("com.zxplat.plugin.elep.host")
public class ElepClientAutoConfiguration {
}
