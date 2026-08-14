/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.product;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 医生站目录「只看库存&gt;0」：将机构取药模板库位下推到产品定义分页 Query。
 *
 * <p>与目录行可开立量展示同属模板库位空间，避免药库有量、药房为 0 仍被 EXISTS 放行。</p>
 */
@Component
@RequiredArgsConstructor
public class DoctorStationCatalogPositiveStockLocationBinder {

    /** 取药科室模板库位解析。 */
    private final ClinicalCatalogDispenseLocationResolver clinicalCatalogDispenseLocationResolver;

    /**
     * 当 {@code positiveQuantityOnly=true} 时写入 {@code positiveQuantityLocationIds}（可为空列表）。
     *
     * @param pageQuery          产品定义分页 Query（已含 positiveQuantityOnly）
     * @param organizationId     开立/接诊科室（可被请求 organizationUnitId 覆盖后的值）
     * @param pharmacyLocationId 可选指定药房
     */
    public void bindIfPositiveQuantityOnly(
        ProductDefinitionPageQuery pageQuery,
        Long organizationId,
        Long pharmacyLocationId
    ) {
        if (pageQuery == null || !Boolean.TRUE.equals(pageQuery.getPositiveQuantityOnly())) {
            return;
        }
        // Pre：模板库位去重后下推；空列表 → 药/耗 Spec EXISTS 恒不命中
        pageQuery.setPositiveQuantityLocationIds(
            clinicalCatalogDispenseLocationResolver.listOrganizationTemplateDefLocationIds(
                organizationId, pharmacyLocationId));
    }
}
