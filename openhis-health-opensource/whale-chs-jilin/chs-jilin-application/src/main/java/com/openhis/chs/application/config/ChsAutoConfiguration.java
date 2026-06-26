/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.chs.application.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * CHS 模块自装配：与 EMR/MRMS 同构，扫描 application + domain + api。
 */
@Configuration
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
@ComponentScan(basePackages = {
        "com.openhis.chs.application",
        "com.openhis.chs.domain",
        "com.openhis.chs.api"
})
@EntityScan(basePackages = "com.openhis.chs.domain")
@EnableJpaRepositories(basePackages = "com.openhis.chs.domain")
public class ChsAutoConfiguration {
}
