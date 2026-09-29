/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.pacsimaging.application.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * PACS 影像扩展 Module 自动装配（对齐 BookingAutoConfiguration）。
 *
 * <p>开关：{@code openhis.modules.pacs.imaging.enabled=true}。</p>
 */
@Configuration
@ConditionalOnProperty(name = "openhis.modules.pacs.imaging.enabled", havingValue = "true")
@EnableConfigurationProperties(ImagingMiddlewareProperties.class)
@ComponentScan(basePackages = {
    "com.openhis.whale.pacsimaging.application",
    "com.openhis.whale.pacsimaging.domain"
})
@EntityScan(basePackages = "com.openhis.whale.pacsimaging.domain")
@EnableJpaRepositories(basePackages = "com.openhis.whale.pacsimaging.domain")
public class ImagingAutoConfiguration {
}
