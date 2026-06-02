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

package com.openhis.health.application.integration.winning.catalog.gateway;

import com.openhis.health.application.integration.winning.catalog.dto.WinningActivityCatalogItemDto;
import com.openhis.health.application.integration.winning.catalog.dto.WinningCatalogFetchResult;
import com.openhis.health.application.integration.winning.catalog.dto.WinningCatalogPullRequest;
import com.openhis.health.application.integration.winning.catalog.dto.WinningDeviceCatalogItemDto;
import com.openhis.health.application.integration.winning.catalog.dto.WinningMedicationCatalogItemDto;

/**
 * 卫宁 HIS 目录拉取网关（集成边界）。
 *
 * <h3>现场对接步骤</h3>
 * <ol>
 *   <li>在本包同级新建实现类，例如 {@code WinningCatalogGatewayHttpImpl}，标注 {@code @Component}。</li>
 *   <li>实现下列三个 {@code fetch*} 方法：调用卫宁 HTTP/SDK，将响应映射为标准化 DTO。</li>
 *   <li>实现 {@link #ping()}：返回 {@code configured=true} 且可访问卫宁。</li>
 *   <li>注册为 Spring Bean 后，将自动替换默认 {@link WinningCatalogGatewayStub}（{@code @ConditionalOnMissingBean}）。</li>
 * </ol>
 *
 * <p>应用层 {@link com.openhis.health.application.integration.winning.catalog.service.WinningCatalogSyncAppService}
 * 只依赖本接口，不直接依赖 HTTP 客户端。</p>
 */
public interface WinningCatalogGateway {

    /**
     * 网关是否已完成现场配置（非 Stub）。
     */
    boolean isLiveGateway();

    /**
     * 连通性探测（现场实现应调用卫宁健康检查或轻量查询）。
     */
    WinningCatalogGatewayPingResult ping();

    /**
     * 拉取药品目录页。
     */
    WinningCatalogFetchResult<WinningMedicationCatalogItemDto> fetchMedicationCatalog(WinningCatalogPullRequest request);

    /**
     * 拉取耗材目录页。
     */
    WinningCatalogFetchResult<WinningDeviceCatalogItemDto> fetchDeviceCatalog(WinningCatalogPullRequest request);

    /**
     * 拉取诊疗目录页。
     */
    WinningCatalogFetchResult<WinningActivityCatalogItemDto> fetchActivityCatalog(WinningCatalogPullRequest request);

    /**
     * 网关探测结果。
     */
    record WinningCatalogGatewayPingResult(boolean reachable, String message) {
    }
}
