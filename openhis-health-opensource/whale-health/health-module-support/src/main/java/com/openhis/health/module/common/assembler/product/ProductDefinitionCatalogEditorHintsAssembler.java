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

import com.openhis.health.module.common.assembler.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.module.common.dto.product.ProductDefinitionCatalogEditorHintsDto;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionSelectionTerminologyMaps;
import org.apache.commons.lang3.StringUtils;

public final class ProductDefinitionCatalogEditorHintsAssembler {

    /**
     * 工具类禁止实例化；装配入口均为 {@code static}。
     */
    private ProductDefinitionCatalogEditorHintsAssembler() {
    }

    /**
     * 由读模型构造目录提示；{@link ProductDefinitionCatalogEditorHintsDto#getPurchasePrice()} 恒为 {@code null}，由应用层补价。
     *
     * @param pr 产品定义读模型行，可为 null
     * @return 提示 DTO；{@code pr} 为 null 时返回 null
     */
    /**
     * 由主表批量 DTO 构造目录提示（读模型迁移路径）。
     */
    public static ProductDefinitionCatalogEditorHintsDto fromDisplayDto(ProductDefinitionDisplayDto row) {
        return fromDisplayDto(row, null);
    }

    /**
     * 由主表 DTO 构造目录提示；{@code terminologyMaps} 非空时对单位槽按 UNIT_CODE 术语回填展示名（B.10.8.1）。
     *
     * @param row              产品定义读模型行
     * @param terminologyMaps  预热术语映射，可为 null
     * @return 提示 DTO；{@code row} 为 null 时返回 null
     */
    public static ProductDefinitionCatalogEditorHintsDto fromDisplayDto(
        ProductDefinitionDisplayDto row,
        ProductDefinitionSelectionTerminologyMaps terminologyMaps
    ) {
        if (row == null) {
            return null;
        }
        ProductDefinitionCatalogEditorHintsDto dto = new ProductDefinitionCatalogEditorHintsDto();
        dto.setUnitBands(ProductDefinitionCatalogUnitBandsAssembler.buildInboundSelectionBandsWithTerminology(
            row, terminologyMaps));
        dto.setProductBasePrice(row.getPrice());
        dto.setMinUnitCode(trimOrNull(row.getSplitUnitCode()));
        dto.setDefaultPurchaseUnitCode(trimOrNull(row.getPurchaseUnitCode()));
        dto.setPurchaseUnitRatioToSplit(row.getPurchaseUnitRatio());
        dto.setMerchandiseName(trimOrNull(row.getMerchandiseName()));
        dto.setCategoryCode(trimOrNull(row.getCategoryCode()));
        dto.setCategoryCodeDictText(trimOrNull(row.getCategoryCodeDictText()));
        dto.setProductBusNo(trimOrNull(row.getProductBusNo()));
        dto.setTotalVolume(trimOrNull(row.getTotalVolume()));
        dto.setPurchasePrice(null);
        return dto;
    }

    /**
     * 去除首尾空白；全空白或 {@code null} 时返回 {@code null}，与读模型空串脏数据隔离。
     *
     * @param raw 原始串
     * @return 修剪后的非空串，或 {@code null}
     */
    private static String trimOrNull(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        if (StringUtils.isEmpty(t)) {
            return null;
        }
        return t;
    }
}
