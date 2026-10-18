/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.medicaltech;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 注册医技执行科室共看组配置。
 */
@Configuration
@EnableConfigurationProperties(MedicalTechSharedExecutingOrgProperties.class)
public class MedicalTechSharedExecutingOrgConfiguration {
}
