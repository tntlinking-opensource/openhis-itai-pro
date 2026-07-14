/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot 入口。
 * <p>Whale 框架与 HIS 核心分域扫描；EMR/CHS 等扩展模块经各自 {@code AutoConfiguration} 自装配。</p>
 */
@SpringBootApplication
@ComponentScan(
        basePackages = {
                "com.openhis.whale",
                "com.openhis.health.domain",
                "com.openhis.health.application",
                "com.openhis.health.infrastructure",
                "com.openhis.health.config"
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.openhis\\.whale\\..*\\.infrastructure\\.repository\\..*"
        )
)
@EntityScan(basePackages = "com.openhis.health.domain")
@EnableJpaRepositories(basePackages = "com.openhis.health.domain")
@EnableJpaAuditing
@EnableCaching
@EnableAsync
@EnableScheduling
public class HealthApplication {

    /**
     * Spring Boot 启动入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(HealthApplication.class, args);
    }
}
