/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.audit.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@AutoConfiguration
@ComponentScan(
        basePackages = "com.zxplat.framework.audit",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.zxplat\\.framework\\.audit\\.infrastructure\\.repository\\..*"
        )
)
@EntityScan(basePackages = "com.zxplat.framework.audit")
@EnableJpaRepositories(basePackages = "com.zxplat.framework.audit.infrastructure.repository")
public class ZxplatAuditAutoConfiguration {
}
