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

package com.openhis.health.module.common.assembler.product;

import com.openhis.health.domain.custom.servicelocationtemplate.service.ServiceLocationTemplateQueryService;
import com.openhis.health.domain.share.constants.MedicationCommonConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 临床开嘱/目录/定价共用的取药科室模板库位解析（{@code DM-CUS-006}）。
 *
 * <p>模板 {@code distribution_category_code} 与产品目录 {@code categoryCode} 对齐（药品/耗材类别）；
 * 候选库位经 calcScore（具体项 + 用法）过滤；默认库位经 {@code matchDefaultDistributionLocation} 择优。</p>
 */
@Component
@RequiredArgsConstructor
public class ClinicalCatalogDispenseLocationResolveSupport {

    private final ServiceLocationTemplateQueryService serviceLocationTemplateQueryService;

    /**
     * 列出对当前产品适用（calcScore≥0）的候选发药库位；可选单点药房收窄。
     *
     * @param orgId                开立/接诊科室
     * @param productRow           产品读模型（表名、categoryCode、定义 id、默认用法）
     * @param pharmacyLocationId   可选指定药房
     * @return 候选库位 id 列表；机构或产品缺失时为空
     */
    public List<Long> listApplicableCandidateLocationIds(
        Long orgId,
        ProductDefinitionDisplayDto productRow,
        Long pharmacyLocationId
    ) {
        return this.listApplicableCandidateLocationIds(
            orgId,
            productRow != null ? productRow.getProductTableName() : null,
            this.resolveDistributionCategory(productRow),
            productRow != null ? productRow.getProductDefinitionId() : null,
            productRow != null ? productRow.getMethodCode() : null,
            pharmacyLocationId
        );
    }

    /**
     * 按物品表 + categoryCode + 具体项 + 用法列出适用候选库位。
     *
     * @param orgId                  开立/接诊科室
     * @param productTableName       物品主表名
     * @param categoryCode           目录 categoryCode（可空回落默认）
     * @param productDefinitionId    产品定义主键
     * @param methodCode             给药途径（耗材可空）
     * @param pharmacyLocationId     可选指定药房
     * @return 候选库位 id 列表
     */
    public List<Long> listApplicableCandidateLocationIds(
        Long orgId,
        String productTableName,
        String categoryCode,
        Long productDefinitionId,
        String methodCode,
        Long pharmacyLocationId
    ) {
        if (orgId == null || StringUtils.isBlank(productTableName)) {
            return List.of();
        }
        String distributionCategory = StringUtils.firstNonBlank(
            categoryCode,
            MedicationCommonConstants.DEFAULT_SERVICE_LOCATION_DISTRIBUTION_CATEGORY_CODE
        );
        List<Long> locs = serviceLocationTemplateQueryService.listApplicableDistributionLocationIds(
            orgId,
            productTableName,
            distributionCategory,
            productDefinitionId,
            methodCode,
            null
        );
        if (pharmacyLocationId != null && locs.contains(pharmacyLocationId)) {
            // 读侧用户指定库房且在模板候选内：收窄为单点
            return List.of(pharmacyLocationId);
        }
        return locs;
    }

    /**
     * 解析默认发药/发材库位（calcScore 择优）。
     *
     * @param orgId      开立/接诊科室
     * @param productRow 产品读模型
     * @return 默认库位；无适用规则时为 null
     */
    public Long matchDefaultDispenseLocationId(Long orgId, ProductDefinitionDisplayDto productRow) {
        if (orgId == null || productRow == null || StringUtils.isBlank(productRow.getProductTableName())) {
            return null;
        }
        return serviceLocationTemplateQueryService.matchDefaultDistributionLocation(
            orgId,
            productRow.getProductTableName(),
            this.resolveDistributionCategory(productRow),
            productRow.getProductDefinitionId(),
            productRow.getMethodCode(),
            null
        );
    }

    /**
     * 解析耗材默认发放库房（无用法维度）。
     *
     * @param orgId               开立/接诊科室
     * @param deviceDefinitionId  耗材定义主键
     * @param categoryCode        目录 categoryCode（可空回落默认）
     * @return 默认库位；无适用规则时为 null
     */
    public Long matchDefaultDeviceDispenseLocationId(
        Long orgId,
        Long deviceDefinitionId,
        String categoryCode
    ) {
        if (orgId == null || deviceDefinitionId == null) {
            return null;
        }
        String distributionCategory = StringUtils.firstNonBlank(
            categoryCode,
            MedicationCommonConstants.DEFAULT_SERVICE_LOCATION_DISTRIBUTION_CATEGORY_CODE
        );
        return serviceLocationTemplateQueryService.matchDefaultDistributionLocation(
            orgId,
            TableNameConstants.ADM_DEVICE_DEFINITION,
            distributionCategory,
            deviceDefinitionId,
            null,
            null
        );
    }

    /**
     * 产品 categoryCode → 取药科室模板 distribution_category_code；空则回落默认分类常量。
     *
     * @param productRow 产品读模型
     * @return 模板分布类别 code
     */
    private String resolveDistributionCategory(ProductDefinitionDisplayDto productRow) {
        if (productRow == null || StringUtils.isBlank(productRow.getCategoryCode())) {
            return MedicationCommonConstants.DEFAULT_SERVICE_LOCATION_DISTRIBUTION_CATEGORY_CODE;
        }
        return productRow.getCategoryCode();
    }
}
