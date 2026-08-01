/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.inventoryopening.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.domain.share.product.dto.ProductDefinitionCatalogEditorHintsDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存期初详情明细行（IF-BAS-INV-OPN-03）。
 *
 * <p>展示扩展（厂家、单位名、{@code productCatalog}）与进货详情对齐，供前端多计量带回显与扫码应扫数（贴码单位在 catalog）。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "库存期初详情行")
public class InventoryOpeningDetailLineDto {

    /** 物品来源表名。 */
    @Schema(description = "物品来源表")
    private String itemTable;

    /** 物品定义主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "物品定义主键")
    private Long itemId;

    /** 品名展示。 */
    @Schema(description = "品名")
    private String itemName;

    /** 规格展示。 */
    @Schema(description = "规格")
    private String specification;

    /** 厂家合一展示（产品读模型）。 */
    @Schema(description = "厂家")
    private String manufacturer;

    /** 批号。 */
    @Schema(description = "批号")
    private String lotNumber;

    /** 效期。 */
    @Schema(description = "效期")
    private LocalDateTime endTime;

    /** 生产日期。 */
    @Schema(description = "生产日期")
    private LocalDateTime startTime;

    /** 数量（拆零）。 */
    @Schema(description = "数量")
    private BigDecimal itemQuantity;

    /** 单位编码。 */
    @Schema(description = "单位")
    private String unitCode;

    /** 计量单位展示名（术语侧）。 */
    @Schema(description = "单位展示名")
    private String unitCodeDictText;

    /** 进价。 */
    @Schema(description = "进价")
    private BigDecimal price;

    /**
     * 已废弃：详情不再回显独立售价（与进货一致）；批号售价在定价表 LOT_NUMBER_PRICE。
     */
    @Schema(description = "售价（废弃，恒空）")
    private BigDecimal salePrice;

    /** 行金额。 */
    @Schema(description = "行金额")
    private BigDecimal totalPrice;

    /** 追溯码。 */
    @Schema(description = "追溯码")
    private String traceNo;

    /** 供应商主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "供应商")
    private Long supplierId;

    /**
     * 产品目录编辑提示（含 unitBands）；槽价锚本行持久化 {@code price}/{@code unitCode}，不查定价表当前价。
     */
    @Schema(description = "产品目录")
    private ProductDefinitionCatalogEditorHintsDto productCatalog;
}
