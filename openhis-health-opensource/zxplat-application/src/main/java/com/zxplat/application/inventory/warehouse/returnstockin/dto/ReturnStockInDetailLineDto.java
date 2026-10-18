/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.returnstockin.dto;

import com.zxplat.domain.share.product.dto.ProductDefinitionCatalogEditorHintsDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退库入库壳单详情响应中的明细行（IF-WHS-RSI-03：只读，含产品名称富化）。
 *
 * <p>数量/单价取自供应请求落库字段，展示时不走当前目录价读路径（BR-PRICE-SRC-001）。</p>
 */
@Getter
@Setter
@Schema(description = "退库入库详情明细行")
public class ReturnStockInDetailLineDto {

    /** 明细主键 id。 */
    @Schema(description = "明细主键 id")
    private Long id;

    /** 物品表 itemTable。 */
    @Schema(description = "物品表 itemTable")
    private String itemTable;

    /** 物品 itemId。 */
    @Schema(description = "物品 itemId")
    private Long itemId;

    /** 退库数量 itemQuantity。 */
    @Schema(description = "退库数量 itemQuantity")
    private BigDecimal itemQuantity;

    /** 计量单位 unitCode。 */
    @Schema(description = "计量单位 unitCode")
    private String unitCode;

    /** 批号 lotNumber。 */
    @Schema(description = "批号 lotNumber")
    private String lotNumber;

    /** 效期起 startTime。 */
    @Schema(description = "效期起 startTime")
    private LocalDateTime startTime;

    /** 效期止 endTime。 */
    @Schema(description = "效期止 endTime")
    private LocalDateTime endTime;

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

    /** 计量单位展示名（详情数量列回显；由产品读模型解析，禁止用 unitCode 冒充）。 */
    @Schema(description = "计量单位展示名")
    private String unitCodeDictText;

    /**
     * 产品目录编辑提示（含 unitBands）。
     *
     * <p>供前端 {@code hydrateInventoryDetailLineQtyForGrid} 将落库 SPLIT 数量换算为展示计量带；
     * 不改变 {@link #itemQuantity} 落库语义，不读当前目录价覆盖行价（BR-PRICE-SRC-001）。</p>
     */
    @Schema(description = "产品目录 productCatalog")
    private ProductDefinitionCatalogEditorHintsDto productCatalog;
}
