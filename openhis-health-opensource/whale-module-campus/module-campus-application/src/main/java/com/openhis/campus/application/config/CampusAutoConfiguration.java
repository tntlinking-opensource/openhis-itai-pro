/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.application.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@ConditionalOnProperty(name = "openhis.modules.campus.enabled", havingValue = "true")
@ComponentScan({"com.openhis.campus.application", "com.openhis.campus.domain"})
@EntityScan("com.openhis.campus.domain")
@EnableJpaRepositories("com.openhis.campus.domain")
public class CampusAutoConfiguration {
}
