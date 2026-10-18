/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.product;

import com.zxplat.domain.share.product.dto.ProductDefinitionCatalogEditorHintsDto;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.domain.share.product.dto.ProductDefinitionSelectionTerminologyMaps;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

/**
 * 产品目录编辑提示装配：由 {@link ProductDefinitionDisplayDto} 构造 {@link ProductDefinitionCatalogEditorHintsDto}，
 * 单位槽与术语展示委托 {@link ProductDefinitionCatalogUnitBandsAssembler}。
 */
public final class ProductDefinitionCatalogEditorHintsAssembler {

    /**
     * 工具类禁止实例化；装配入口均为 {@code static}。
     */
    private ProductDefinitionCatalogEditorHintsAssembler() {
    }

    /**
     * 由主表读模型构造目录提示（无术语预热）；{@code purchasePrice} 恒为 null，由应用层补价。
     *
     * @param row 产品定义读模型行，可为 null
     * @return 提示 DTO；{@code row} 为 null 时返回 null
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
        dto.setMinUnitCode(trimToNull(row.getSplitUnitCode()));
        dto.setDefaultPurchaseUnitCode(trimToNull(row.getPurchaseUnitCode()));
        dto.setPurchaseUnitRatioToSplit(row.getPurchaseUnitRatio());
        dto.setMerchandiseName(trimToNull(row.getMerchandiseName()));
        dto.setCategoryCode(trimToNull(row.getCategoryCode()));
        dto.setCategoryCodeDictText(trimToNull(row.getCategoryCodeDictText()));
        dto.setProductBusNo(trimToNull(row.getProductBusNo()));
        dto.setTotalVolume(trimToNull(row.getTotalVolume()));
        // 追溯贴码：供前端扫码应扫数；建码时服务端另从药典写入主档快照
        dto.setTraceUnitCode(trimToNull(row.getTraceUnitCode()));
        dto.setTraceUnitRatio(row.getTraceUnitRatio());
        dto.setPurchasePrice(null);
        return dto;
    }
}
