/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.permission.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@AutoConfiguration
@ComponentScan(
        basePackages = "com.openhis.whale.permission",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.openhis\\.whale\\.permission\\.infrastructure\\.repository\\..*"
        )
)
@EntityScan(basePackages = "com.openhis.whale.permission")
@EnableJpaRepositories(basePackages = "com.openhis.whale.permission.infrastructure.repository")
public class WhalePermissionAutoConfiguration {
}
