/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.returntosupplier.dto;

import com.openhis.health.domain.share.product.dto.ProductDefinitionCatalogEditorHintsDto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 药库退供应商单详情响应中的明细行（IF-WHS-RT-03：只读，含产品名称富化）。
 *
 * <p>金额取自供应请求落库 {@code price}，展示时不走当前目录价读路径（BR-PRICE-SRC-001）。</p>
 *
 * <p>{@code unitCodeDictText}/{@code productCatalog} 由应用层按产品读模型批量命中后填充，供前端
 * SPLIT→录入单位换算与多计量带下拉；未命中时保持 null。</p>
 */
@Getter
@Setter
public class ReturnToSupplierDetailLineDto {
    /** 明细主键 id。 */
    @Schema(description = "明细主键 id")
    private Long id;

    /** 物品表 itemTable。 */
    @Schema(description = "物品表 itemTable")
    private String itemTable;

    /** 物品 itemId。 */
    @Schema(description = "物品 itemId")
    private Long itemId;

    /** 退货数量 itemQuantity。 */
    @Schema(description = "退货数量 itemQuantity")
    private BigDecimal itemQuantity;

    /** 计量单位 unitCode。 */
    @Schema(description = "计量单位 unitCode")
    private String unitCode;

    /** 计量单位展示名 unitCodeDictText。 */
    @Schema(description = "计量单位展示名 unitCodeDictText")
    private String unitCodeDictText;

    /** 批号 lotNumber。 */
    @Schema(description = "批号 lotNumber")
    private String lotNumber;

    /** 追溯码 traceNo。 */
    @Schema(description = "追溯码 traceNo")
    private String traceNo;

    /** 发生时间 occurrenceTime。 */
    @Schema(description = "发生时间 occurrenceTime")
    private LocalDateTime occurrenceTime;

    /** 原因 reason。 */
    @Schema(description = "原因 reason")
    private String reason;

    /** 备注 remake。 */
    @Schema(description = "备注 remake")
    private String remake;

    /** 产品名称 productName。 */
    @Schema(description = "产品名称 productName")
    private String productName;

    /**
     * 单价（包装锚进价快照）：取供应请求落库 {@code price}；优先来源进货行，缺省为批号进价（BR-WHS-RT-009）。
     */
    @Schema(description = "单价（包装锚进价） price")
    private BigDecimal price;

    /**
     * 行合计金额：取供应请求落库 {@code total_price}（拆零量 × 包装锚单价，BR-MON-PKG-001）。
     */
    @Schema(description = "行合计 totalPrice")
    private BigDecimal totalPrice;

    /** 产品目录 productCatalog（含 unitBands，详情单位回显用）。 */
    @Schema(description = "产品目录 productCatalog")
    private ProductDefinitionCatalogEditorHintsDto productCatalog;
}
