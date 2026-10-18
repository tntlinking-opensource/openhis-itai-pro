/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * 领域基础设施通用装配：模块化 Flyway 与迁移顺序钩子。
 */
@AutoConfiguration
@ComponentScan(basePackages = "com.zxplat.domain.infrastructure.config")
public class DomainInfrastructureAutoConfiguration {
}
