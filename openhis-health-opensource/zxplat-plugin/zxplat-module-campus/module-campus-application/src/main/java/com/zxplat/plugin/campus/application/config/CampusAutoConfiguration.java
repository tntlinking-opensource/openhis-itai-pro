/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.application.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@ConditionalOnProperty(name = "zxplat.modules.campus.enabled", havingValue = "true", matchIfMissing = true)
@ComponentScan({"com.zxplat.plugin.campus.application", "com.zxplat.plugin.campus.domain"})
@EntityScan("com.zxplat.plugin.campus.domain")
@EnableJpaRepositories("com.zxplat.plugin.campus.domain")
public class CampusAutoConfiguration {
}
