/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.insurance.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 医保协同模块 Spring 配置：启用 {@link InsuranceCoordinationProperties} 绑定。
 */
@Configuration
@EnableConfigurationProperties(InsuranceCoordinationProperties.class)
public class InsuranceCoordinationConfiguration {
}
