/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * whale-core {@code @Component} 注册（与 {@link WhaleCoreAutoConfiguration} 分文件且优先加载，
 * 避免 {@code domainEventPublisher} 等 {@code @Bean} 创建时 {@link com.openhis.whale.core.infrastructure.security.ICurrentUser} 尚未注册）。
 */
@AutoConfiguration
@ComponentScan(
        basePackages = "com.openhis.whale.core",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.openhis\\.whale\\.core\\.infrastructure\\.sequence\\.WhaleSequenceRepository"
        )
)
public class WhaleCoreComponentRegistrationConfiguration {
}
