/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.inventoryopening.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.domain.share.product.dto.ProductDefinitionCatalogUnitBandDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * 库存期初选品行（IF-BAS-INV-OPN-11）。
 *
 * <p>字段口径对齐药库进货选品（单位槽 {@code unitBands}、目录进价），便于前端按进货标杆回显。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "库存期初选品行")
public class InventoryOpeningProductRowDto {

    /** 物品来源表名。 */
    @Schema(description = "物品表")
    private String itemTable;

    /** 物品定义主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "物品主键")
    private Long itemId;

    /** 目录业务编号（与进货选品 productBusNo 同义）。 */
    @Schema(description = "目录业务编号")
    private String productBusNo;

    /** 品名。 */
    @Schema(description = "品名")
    private String itemName;

    /** 规格。 */
    @Schema(description = "规格")
    private String specification;

    /** 药品分类码（western/chinese-patent/chinese-herbal；耗材可空）。 */
    @Schema(description = "分类码")
    private String categoryCode;

    /** 物资分类展示名（术语侧）。 */
    @Schema(description = "分类展示名")
    private String categoryCodeDictText;

    /** 默认/拆零单位编码。 */
    @Schema(description = "单位")
    private String unitCode;

    /** 采购进价（与进货选品 purchasePrice 同义；每包装）。 */
    @Schema(description = "采购进价")
    private BigDecimal purchasePrice;

    /** 生产厂家。 */
    @Schema(description = "厂家")
    private String manufacturer;

    /**
     * 目录单位四槽（含槽单价）；期初选品与进货同装，供前端多计量带。
     */
    @Schema(description = "单位四槽")
    private List<ProductDefinitionCatalogUnitBandDto> unitBands;

    /**
     * 追溯码贴码单位（药典）。
     */
    @Schema(description = "追溯码贴码单位")
    private String traceUnitCode;

    /**
     * 1 个追溯码单位对应的拆零数量。
     */
    @Schema(description = "追溯码单位→拆零倍数")
    private BigDecimal traceUnitRatio;
}
