/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@ConditionalOnProperty(name = "zxplat.modules.peis.enabled", havingValue = "true", matchIfMissing = true)
@ComponentScan(basePackages = {
        "com.zxplat.plugin.peis.application",
        "com.zxplat.plugin.peis.domain"
})
@EntityScan(basePackages = "com.zxplat.plugin.peis.domain")
@EnableJpaRepositories(basePackages = "com.zxplat.plugin.peis.domain")
public class PeisAutoConfiguration {
}