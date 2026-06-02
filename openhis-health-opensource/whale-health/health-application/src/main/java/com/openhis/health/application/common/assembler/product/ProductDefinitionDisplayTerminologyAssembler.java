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

package com.openhis.health.application.common.assembler.product;

import com.openhis.health.application.common.assembler.order.OrderCatalogDisplayAssembler;
import com.openhis.health.application.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.application.querysupport.product.dto.ProductDefinitionSelectionTerminologyMaps;
import com.openhis.health.application.querysupport.product.ProductDefinitionDisplaySupport;
import com.openhis.health.domain.share.constants.TableNameConstants;
import org.apache.commons.lang3.StringUtils;

import java.util.Collection;

/**
 * 产品定义展示 DTO：术语字典列批量回填（B.6.1 / B.10.8.1）。
 *
 * <p>主表映射 {@link ProductDefinitionDisplaySupport}
 * 仅承载持久化 {@code code}；展示名须在本装配器或等价预热路径上，经
 * {@link com.openhis.health.application.common.assembler.option.TerminologyOptionLoader#buildLabelMap}
 * 解析后写入各 {@code *DictText} 字段。</p>
 */
public final class ProductDefinitionDisplayTerminologyAssembler {

    private ProductDefinitionDisplayTerminologyAssembler() {
    }

    /**
     * 批量回填展示 DTO 的字典/术语列。
     *
     * @param rows  展示行集合，可含 null
     * @param maps  预热术语映射；为 null 时跳过
     */
    public static void enrichAll(
        Collection<ProductDefinitionDisplayDto> rows,
        ProductDefinitionSelectionTerminologyMaps maps
    ) {
        if (rows == null || rows.isEmpty() || maps == null) {
            return;
        }
        for (ProductDefinitionDisplayDto row : rows) {
            enrich(row, maps);
        }
    }

    /**
     * 单条展示 DTO 回填字典/术语列。
     *
     * @param row  展示 DTO
     * @param maps 预热术语映射
     */
    public static void enrich(ProductDefinitionDisplayDto row, ProductDefinitionSelectionTerminologyMaps maps) {
        if (row == null || maps == null) {
            return;
        }
        row.setCategoryCodeDictText(ProductDefinitionCatalogUnitBandsAssembler.resolveProductCategoryDictText(
            row.getProductTableName(), row.getCategoryCode(), maps));

        applyUnitDictText(row, maps.getUnitLabelByCode());

        if (!TableNameConstants.MED_MEDICATION_DEFINITION.equals(row.getProductTableName())) {
            if (TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(row.getProductTableName())) {
                if (StringUtils.isBlank(row.getCategoryCodeDictText())) {
                    row.setCategoryCodeDictText(OrderCatalogDisplayAssembler.resolveCategoryDisplay(
                        maps.getActivityCategoryLabelByCode(), row.getCategoryCode()));
                }
            }
            return;
        }

        row.setRateCodeDictText(OrderCatalogDisplayAssembler.resolveCategoryDisplay(
            maps.getRateLabelByCode(), row.getRateCode()));
        row.setMethodCodeDictText(OrderCatalogDisplayAssembler.resolveCategoryDisplay(
            maps.getMethodLabelByCode(), row.getMethodCode()));
        row.setDoseFormCodeDictText(OrderCatalogDisplayAssembler.resolveCategoryDisplay(
            maps.getDoseFormLabelByCode(), row.getDoseFormCode()));
        row.setRestrictedEnumDictText(OrderCatalogDisplayAssembler.resolveCategoryDisplay(
            maps.getRestrictedEnumLabelByCode(), row.getRestrictedEnum()));
        row.setPartAttributeEnumDictText(OrderCatalogDisplayAssembler.resolveCategoryDisplay(
            maps.getPartAttributeLabelByCode(), row.getPartAttributeEnum()));
        row.setThoPartAttributeEnumDictText(OrderCatalogDisplayAssembler.resolveCategoryDisplay(
            maps.getThoPartAttributeLabelByCode(), row.getThoPartAttributeEnum()));
        row.setChrgitmLvText(resolveChrgitmLvText(maps.getChrgitmLvLabelByCode(), row.getChrgitmLv()));

        if (StringUtils.isNotBlank(row.getDoseUnitCode())) {
            String doseName = OrderCatalogDisplayAssembler.resolveCategoryDisplay(
                maps.getDoseUnitLabelByCode(), row.getDoseUnitCode());
            if (StringUtils.isBlank(doseName)) {
                doseName = OrderCatalogDisplayAssembler.resolveCategoryDisplay(
                    maps.getUnitLabelByCode(), row.getDoseUnitCode());
            }
            row.setDoseUnitCodeDictText(doseName);
        }
    }

    /**
     * 按持久化单位码回填各业务单位展示列（优先 {@code UNIT_CODE} 术语表）。
     *
     * @param row           展示 DTO
     * @param unitLabelByCode 单位术语映射
     */
    private static void applyUnitDictText(ProductDefinitionDisplayDto row, java.util.Map<String, String> unitLabelByCode) {
        row.setPurchaseUnitCodeDictText(resolveUnitLabel(unitLabelByCode, row.getPurchaseUnitCode()));
        row.setSplitUnitCodeDictText(resolveUnitLabel(unitLabelByCode, row.getSplitUnitCode()));
        row.setPackageUnitCodeDictText(resolveUnitLabel(unitLabelByCode, row.getPackageUnitCode()));
        row.setStockUnitCodeDictText(resolveUnitLabel(unitLabelByCode, row.getStockUnitCode()));
        row.setDefEncounterUnitCodeDictText(resolveUnitLabel(unitLabelByCode, row.getDefEncounterUnitCode()));
        row.setDefInhospitalUnitCodeDictText(resolveUnitLabel(unitLabelByCode, row.getDefInhospitalUnitCode()));
    }

    private static String resolveUnitLabel(java.util.Map<String, String> unitLabelByCode, String unitCode) {
        return OrderCatalogDisplayAssembler.resolveCategoryDisplay(unitLabelByCode, unitCode);
    }

    private static String resolveChrgitmLvText(java.util.Map<String, String> chrgitmLvLabelByCode, Integer chrgitmLv) {
        if (chrgitmLv == null || chrgitmLvLabelByCode == null || chrgitmLvLabelByCode.isEmpty()) {
            return null;
        }
        return OrderCatalogDisplayAssembler.resolveCategoryDisplay(chrgitmLvLabelByCode, String.valueOf(chrgitmLv));
    }
}
