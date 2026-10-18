/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.lis.application.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@ConditionalOnProperty(name = "zxplat.modules.lis.enabled", havingValue = "true", matchIfMissing = true)
@ComponentScan(basePackages = {
        "com.zxplat.plugin.lis.application",
        "com.zxplat.plugin.lis.domain"
})
@EntityScan(basePackages = "com.zxplat.plugin.lis.domain")
@EnableJpaRepositories(basePackages = "com.zxplat.plugin.lis.domain")
public class LisAutoConfiguration {
}