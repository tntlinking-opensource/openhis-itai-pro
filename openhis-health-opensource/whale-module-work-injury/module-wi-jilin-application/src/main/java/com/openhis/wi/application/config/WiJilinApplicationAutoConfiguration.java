/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.application.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/** 吉林工伤 UI AppService 装配。 */
@Configuration
@ConditionalOnProperty(name = "openhis.modules.work-injury.enabled", havingValue = "true")
@ComponentScan(basePackages = "com.openhis.wi.application")
public class WiJilinApplicationAutoConfiguration {
}
