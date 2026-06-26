/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.mrms.application.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * MRMS 模块自装配：扫描应用层与领域层。
 */
@Configuration
@ConditionalOnProperty(name = "openhis.modules.mrms.enabled", havingValue = "true")
@EntityScan(basePackages = "com.openhis.whale.mrms.domain.entity")
@EnableJpaRepositories(basePackages = "com.openhis.whale.mrms.domain.repository")
@ComponentScan(basePackages = {
        "com.openhis.whale.mrms.application",
        "com.openhis.whale.mrms.domain"
})
public class MrmsAutoConfiguration {
}
