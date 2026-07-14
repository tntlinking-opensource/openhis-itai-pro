/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.application.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/** CHS 医保 UI AppService 装配 */
@Configuration
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
@ComponentScan(basePackages = "com.openhis.chs.application")
public class ChsJilinApplicationAutoConfiguration {
}
