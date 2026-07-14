/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.application.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 传染病前置模块自动配置（embedded 模式）。
 */
@Configuration
@ConditionalOnProperty(name = "openhis.modules.crb.enabled", havingValue = "true")
@EnableAsync
@ComponentScan(basePackages = {
        "com.openhis.whale.crb.application",
        "com.openhis.whale.crb.domain"
})
@EntityScan(basePackages = "com.openhis.whale.crb.domain")
@EnableJpaRepositories(basePackages = "com.openhis.whale.crb.domain")
public class CrbAutoConfiguration {
}
