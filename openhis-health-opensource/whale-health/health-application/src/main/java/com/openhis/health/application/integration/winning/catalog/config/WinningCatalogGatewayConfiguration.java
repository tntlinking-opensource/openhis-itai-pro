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

package com.openhis.health.application.integration.winning.catalog.config;

import com.openhis.health.application.integration.winning.catalog.gateway.WinningCatalogGateway;
import com.openhis.health.application.integration.winning.catalog.gateway.WinningCatalogGatewayStub;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 卫宁目录网关 Bean 注册：默认 Stub；现场提供 {@link WinningCatalogGateway} 实现 Bean 后自动替换。
 */
@Configuration
public class WinningCatalogGatewayConfiguration {

    @Bean
    @ConditionalOnMissingBean(WinningCatalogGateway.class)
    public WinningCatalogGateway winningCatalogGateway() {
        return new WinningCatalogGatewayStub();
    }
}
