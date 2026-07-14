/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.runtime.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@ConditionalOnProperty(prefix = "openhis.framework.runtime", name = "collector-enabled", havingValue = "true")
@EntityScan(basePackages = "com.openhis.whale.runtime.collector.domain")
@EnableJpaRepositories(basePackages = "com.openhis.whale.runtime.collector.repository")
public class WhaleRuntimeCollectorAutoConfiguration {
}
