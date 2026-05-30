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

package com.openhis.health.bridge.emr.config;

import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * HIS→EMR 桥接装配：先于 EMR 模块注册 {@code EmrMetadataContextPort} 实现，避免 NoOp 占位。
 */
@Configuration
@ConditionalOnClass(name = "com.openhis.whale.emr.domain.metadata.port.EmrMetadataContextPort")
@ConditionalOnProperty(name = "openhis.modules.emr.enabled", havingValue = "true")
@AutoConfigureBefore(name = "com.openhis.whale.emr.application.config.EmrAutoConfiguration")
@ComponentScan("com.openhis.health.application.emr")
public class HealthEmrBridgeAutoConfiguration {
}
