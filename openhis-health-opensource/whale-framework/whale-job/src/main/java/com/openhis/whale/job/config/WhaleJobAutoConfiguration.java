/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.job.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Whale 定时任务管理模块自动配置。
 *
 * <p>任务定义读写 {@code whale_qrtz_*}；执行历史写入 {@code whale_scheduled_job_logs}。</p>
 */
@AutoConfiguration
@ConditionalOnProperty(name = "app.quartz.enabled", havingValue = "true", matchIfMissing = true)
@ComponentScan(
        basePackages = "com.openhis.whale.job",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.openhis\\.whale\\.job\\.infrastructure\\.repository\\..*"
        )
)
@EntityScan(basePackages = "com.openhis.whale.job.domain")
@EnableJpaRepositories(basePackages = "com.openhis.whale.job.infrastructure.repository")
public class WhaleJobAutoConfiguration {
}
