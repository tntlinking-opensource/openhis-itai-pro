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
 * 卫宁目录 HTTP/SDK 网关<strong>现场实现模板</strong>（默认<strong>不注册</strong>为 Spring Bean）。
 *
 * <h3>启用步骤</h3>
 * <ol>
 *   <li>取消类上的注释：{@code @Component}（或改用 {@code @Profile("winning-catalog-live")}）。</li>
 *   <li>注入卫宁客户端（RestTemplate / WebClient / 厂商 SDK）。</li>
 *   <li>在 {@link #fetchMedicationCatalog} 等方法内调用卫宁接口并映射为标准化 DTO。</li>
 *   <li>实现 {@link #isLiveGateway()} 返回 {@code true}，{@link #ping()} 返回可达。</li>
 * </ol>
 *
 * <p>注册为 {@code @Bean} 或 {@code @Component} 后，
 * {@link com.openhis.health.application.integration.winning.catalog.config.WinningCatalogGatewayConfiguration}
 * 中的 Stub 将因 {@code @ConditionalOnMissingBean} 自动停用。</p>
 */
// @Component  // 现场启用：取消注释，或在本类上增加 @Bean 配置
public class WinningCatalogGatewayHttpImpl implements WinningCatalogGateway {

    // TODO 现场：@Autowired WinningCatalogClient winningClient;
    // TODO 现场：@Value("${winning.catalog.base-url}") String baseUrl;

    @Override
    public boolean isLiveGateway() {
        return true;
    }

    @Override
    public WinningCatalogGatewayPingResult ping() {
        // TODO 现场：调用卫宁健康检查，例如 GET /api/catalog/health
        return new WinningCatalogGatewayPingResult(false, "WinningCatalogGatewayHttpImpl 尚未实现 ping，请现场补全");
    }

    @Override
    public WinningCatalogFetchResult<WinningMedicationCatalogItemDto> fetchMedicationCatalog(WinningCatalogPullRequest request) {
        // TODO 现场示例：
        // 1. 组装卫宁请求（机构、分页、增量时间）
        // 2. WinningMedicationPageResponse raw = winningClient.fetchDrugs(...)
        // 3. List<WinningMedicationCatalogItemDto> items = raw.getRecords().stream().map(this::toMedicationDto).toList()
        // 4. 填充 externalItemCode / busNo / lotSalePrice 等
        return notImplementedFetch();
    }

    @Override
    public WinningCatalogFetchResult<WinningDeviceCatalogItemDto> fetchDeviceCatalog(WinningCatalogPullRequest request) {
        // TODO 现场：映射卫宁耗材接口
        return notImplementedFetch();
    }

    @Override
    public WinningCatalogFetchResult<WinningActivityCatalogItemDto> fetchActivityCatalog(WinningCatalogPullRequest request) {
        // TODO 现场：映射卫宁诊疗/收费项目接口
        return notImplementedFetch();
    }

  /*
   * TODO 现场：私有映射示例
   *
   * private WinningMedicationCatalogItemDto toMedicationDto(WinningRawDrug raw) {
   *     WinningMedicationCatalogItemDto dto = new WinningMedicationCatalogItemDto();
   *     dto.setExternalItemCode(raw.getItemCode());
   *     dto.setBusNo(raw.getHospitalCode());
   *     dto.setName(raw.getItemName());
   *     dto.setCategoryCode(raw.getCategory());
   *     dto.setPurchaseUnitCode(raw.getUnit());
   *     dto.setOrganizationId(raw.getOrgId());
   *     dto.setLotNumber(raw.getLotNo());
   *     dto.setLotSalePrice(raw.getPrice());
   *     return dto;
   * }
   */

    private static <T> WinningCatalogFetchResult<T> notImplementedFetch() {
        WinningCatalogFetchResult<T> result = new WinningCatalogFetchResult<>();
        result.setStubMode(Boolean.FALSE);
        result.setGatewayMessage("WinningCatalogGatewayHttpImpl 尚未实现 fetch*，请现场补全 HTTP/SDK 调用");
        result.setRemoteTotalCount(0);
        return result;
    }
}
