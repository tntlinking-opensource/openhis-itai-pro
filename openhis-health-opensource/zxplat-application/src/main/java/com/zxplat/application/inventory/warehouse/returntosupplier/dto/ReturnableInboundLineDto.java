/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.returntosupplier.dto;

import com.zxplat.domain.share.product.dto.ProductDefinitionCatalogEditorHintsDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 从已同意进货单拉取的退货候选行（IF-WHS-RT-13：含可退余量与源库可用量）。
 *
 * <p>{@code price}/{@code totalPrice} 取自进货行落库价（BR-PRICE-SRC-001）。</p>
 */
@Getter
@Setter
@Schema(description = "可退进货明细行")
public class ReturnableInboundLineDto {

    /** 物品表 itemTable。 */
    @Schema(description = "物品表 itemTable")
    private String itemTable;

    /** 物品 itemId。 */
    @Schema(description = "物品 itemId")
    private Long itemId;

    /** 进货数量 inboundQuantity。 */
    @Schema(description = "进货数量 inboundQuantity")
    private BigDecimal inboundQuantity;

    /** 已退占用量 returnedQuantity。 */
    @Schema(description = "已退占用量 returnedQuantity")
    private BigDecimal returnedQuantity;

    /** 可退余量 returnableQuantity。 */
    @Schema(description = "可退余量 returnableQuantity")
    private BigDecimal returnableQuantity;

    /** 源库可用量 sourceAvailableQuantity。 */
    @Schema(description = "源库可用量 sourceAvailableQuantity")
    private BigDecimal sourceAvailableQuantity;

    /** 计量单位 unitCode。 */
    @Schema(description = "计量单位 unitCode")
    private String unitCode;

    /** 批号 lotNumber。 */
    @Schema(description = "批号 lotNumber")
    private String lotNumber;

    /**
     * 追溯码：IF-WHS-RT-13 由应用层按当前院内剩余码预填（本库优先 / 全院回落）；
     * 非进货当时扫码快照。无码批可为空。
     */
    @Schema(description = "当前剩余追溯码（逗号串；非进货历史快照）")
    private String traceNo;

    /** 单价 price。 */
    @Schema(description = "单价 price")
    private BigDecimal price;

    /** 行合计 totalPrice。 */
    @Schema(description = "行合计 totalPrice")
    private BigDecimal totalPrice;

    /** 发生时间 occurrenceTime。 */
    @Schema(description = "发生时间 occurrenceTime")
    private LocalDateTime occurrenceTime;

    /** 备注 remake。 */
    @Schema(description = "备注 remake")
    private String remake;

    /** 产品名称 productName。 */
    @Schema(description = "产品名称 productName")
    private String productName;

    /** 规格 specification。 */
    @Schema(description = "规格 specification")
    private String specification;

    /** 厂家 manufacturer。 */
    @Schema(description = "厂家 manufacturer")
    private String manufacturer;

    /** 计量单位展示名 unitCodeDictText。 */
    @Schema(description = "计量单位展示名 unitCodeDictText")
    private String unitCodeDictText;

    /** 产品目录 productCatalog。 */
    @Schema(description = "产品目录 productCatalog")
    private ProductDefinitionCatalogEditorHintsDto productCatalog;
}
