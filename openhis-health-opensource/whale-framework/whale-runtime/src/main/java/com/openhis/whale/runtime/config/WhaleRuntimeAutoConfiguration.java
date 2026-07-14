/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.runtime.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@ComponentScan(basePackages = "com.openhis.whale.runtime")
@EnableConfigurationProperties(FrameworkRuntimeProperties.class)
@Import({WhaleRuntimeCollectorAutoConfiguration.class, FrameworkRuntimeAsyncConfiguration.class})
public class WhaleRuntimeAutoConfiguration {
}
