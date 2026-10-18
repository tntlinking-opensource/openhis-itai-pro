/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.doctorstation.surgeryanesth.support;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 注册手术/麻醉麻醉医师科室配置。
 */
@Configuration
@EnableConfigurationProperties(SurgeryAnesthAnesthetistOrgProperties.class)
public class SurgeryAnesthAnesthetistOrgConfiguration {
}
