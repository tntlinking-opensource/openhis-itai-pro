/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacsimaging.application.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * PACS 影像（采集/阅片）自动装配；与 RIS 共用 {@code zxplat.modules.pacs.enabled}。
 *
 * <p>方案 A：`-Pwith-pacs` / classpath 即启用；yml 仅写 {@code zxplat.modules.pacs.enabled=false} 关闭整包。</p>
 */
@Configuration
@ConditionalOnProperty(name = "zxplat.modules.pacs.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ImagingMiddlewareProperties.class)
@ComponentScan(basePackages = {
    "com.zxplat.plugin.pacsimaging.application",
    "com.zxplat.plugin.pacsimaging.domain"
})
@EntityScan(basePackages = "com.zxplat.plugin.pacsimaging.domain")
@EnableJpaRepositories(basePackages = "com.zxplat.plugin.pacsimaging.domain")
public class ImagingAutoConfiguration {
}
