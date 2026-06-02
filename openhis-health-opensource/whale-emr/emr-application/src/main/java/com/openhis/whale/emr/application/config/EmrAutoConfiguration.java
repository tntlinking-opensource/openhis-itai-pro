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

package com.openhis.whale.emr.application.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * EMR 模块自装配：扫描 EMR 应用层与领域层，注册 JPA 实体与仓储。
 */
@Configuration
@ConditionalOnProperty(name = "openhis.modules.emr.enabled", havingValue = "true")
@ComponentScan(basePackages = {
        "com.openhis.whale.emr.application",
        "com.openhis.whale.emr.domain"
})
@EntityScan(basePackages = "com.openhis.whale.emr.domain.entity")
@EnableJpaRepositories(basePackages = "com.openhis.whale.emr.domain.repository")
public class EmrAutoConfiguration {
}
