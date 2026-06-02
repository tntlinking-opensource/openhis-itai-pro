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
 * 卫宁目录网关占位实现：保证编译与联调链路可跑通，不写入卫宁真实数据。
 *
 * <p>由 {@link com.openhis.health.application.integration.winning.catalog.config.WinningCatalogGatewayConfiguration}
 * 注册为 Bean；现场提供其它 {@link WinningCatalogGateway} 实现后将自动替换。</p>
 */
public class WinningCatalogGatewayStub implements WinningCatalogGateway {

    private static final String STUB_HINT =
        "当前为 WinningCatalogGatewayStub。现场请实现 WinningCatalogGateway（HTTP/SDK），"
            + "注册 Spring Bean 后将自动替换本占位类。";

    @Override
    public boolean isLiveGateway() {
        return false;
    }

    @Override
    public WinningCatalogGatewayPingResult ping() {
        return new WinningCatalogGatewayPingResult(false, STUB_HINT);
    }

    @Override
    public WinningCatalogFetchResult<WinningMedicationCatalogItemDto> fetchMedicationCatalog(WinningCatalogPullRequest request) {
        return emptyFetch();
    }

    @Override
    public WinningCatalogFetchResult<WinningDeviceCatalogItemDto> fetchDeviceCatalog(WinningCatalogPullRequest request) {
        return emptyFetch();
    }

    @Override
    public WinningCatalogFetchResult<WinningActivityCatalogItemDto> fetchActivityCatalog(WinningCatalogPullRequest request) {
        return emptyFetch();
    }

    private static <T> WinningCatalogFetchResult<T> emptyFetch() {
        WinningCatalogFetchResult<T> result = new WinningCatalogFetchResult<>();
        result.setStubMode(Boolean.TRUE);
        result.setGatewayMessage(STUB_HINT);
        result.setRemoteTotalCount(0);
        return result;
    }
}
