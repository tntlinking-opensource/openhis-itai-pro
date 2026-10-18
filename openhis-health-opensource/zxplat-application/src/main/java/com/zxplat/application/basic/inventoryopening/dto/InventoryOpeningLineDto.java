/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.inventoryopening.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存期初可写明细行（IF-BAS-INV-OPN-04/05/13/16）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "库存期初明细行")
public class InventoryOpeningLineDto {

    /** 物品来源表名（药品/耗材定义表）。 */
    @Schema(description = "物品来源表")
    private String itemTable;

    /** 物品定义主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "物品定义主键")
    private Long itemId;

    /** 批号（同单内与 itemTable/itemId/supplierId 组成唯一键）。 */
    @Schema(description = "批号")
    private String lotNumber;

    /** 效期止点（有明细时必填 → wkf_supply_request.end_time）。 */
    @Schema(description = "效期")
    private LocalDateTime endTime;

    /** 生产日期（可选 → start_time）。 */
    @Schema(description = "生产日期")
    private LocalDateTime startTime;

    /** 期初数量（拆零口径，须 &gt; 0）。 */
    @Schema(description = "数量（拆零）")
    private BigDecimal itemQuantity;

    /** 计量单位编码。 */
    @Schema(description = "单位编码")
    private String unitCode;

    /** 进价（CABINET_OPENING 硬必填）。 */
    @Schema(description = "进价")
    private BigDecimal price;

    /**
     * 已废弃：期初不再维护独立售价；同意时批号售价与进价同值写入（中药 Herbal AOP 另挂）。
     * 请求体传入将被忽略。
     */
    @Schema(description = "售价（废弃，忽略）")
    private BigDecimal salePrice;

    /** 行金额（可选）。 */
    @Schema(description = "行金额")
    private BigDecimal totalPrice;

    /** 追溯码逗号串。 */
    @Schema(description = "追溯码")
    private String traceNo;

    /** 供应商主键（裁决 1A：硬必填）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "供应商主键")
    private Long supplierId;
}
