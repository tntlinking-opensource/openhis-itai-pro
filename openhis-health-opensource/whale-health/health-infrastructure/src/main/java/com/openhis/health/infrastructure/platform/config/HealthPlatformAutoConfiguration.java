/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.platform.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * HIS 侧 whale-core / domain-shared Port 实现（identity、organization、authorization）。
 */
@AutoConfiguration
@ComponentScan(basePackages = "com.openhis.health.infrastructure.platform")
public class HealthPlatformAutoConfiguration {
}
