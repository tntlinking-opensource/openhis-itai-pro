/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@ConditionalOnProperty(name = "openhis.modules.booking.enabled", havingValue = "true")
@ComponentScan(basePackages = {
        "com.openhis.whale.booking.application",
        "com.openhis.whale.booking.domain"
})
@EntityScan(basePackages = "com.openhis.whale.booking.domain")
@EnableJpaRepositories(basePackages = "com.openhis.whale.booking.domain")
public class BookingAutoConfiguration {
}
