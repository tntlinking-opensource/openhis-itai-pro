/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.application.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * EMR 模块自装配：扫描 EMR 应用层与领域层，注册 JPA 实体与仓储。
 */
@Configuration
@ConditionalOnProperty(name = "zxplat.modules.emr.enabled", havingValue = "true", matchIfMissing = true)
@ComponentScan(basePackages = {
        "com.zxplat.plugin.emr.application",
        "com.zxplat.plugin.emr.domain"
})
@EntityScan(basePackages = "com.zxplat.plugin.emr.domain")
@EnableJpaRepositories(basePackages = "com.zxplat.plugin.emr.domain")
public class EmrAutoConfiguration {
}
