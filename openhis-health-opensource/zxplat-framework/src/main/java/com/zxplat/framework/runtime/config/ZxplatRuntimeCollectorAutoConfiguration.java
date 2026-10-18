/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.runtime.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@ConditionalOnProperty(prefix = "zxplat.framework.runtime", name = "collector-enabled", havingValue = "true")
@EntityScan(basePackages = "com.zxplat.framework.runtime.collector.domain")
@EnableJpaRepositories(basePackages = "com.zxplat.framework.runtime.collector.repository")
public class ZxplatRuntimeCollectorAutoConfiguration {
}
