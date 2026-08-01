/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.advice.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * IF-DOR-11：目录库存行（预减后）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站医嘱目录库存行")
public class DoctorStationAdviceInventoryRowDto {

    @Schema(description = "物品主表名")
    /** 物品主表名。 */
    private String itemTable;

    /** 物品定义主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 物品定义主键。 */
    @Schema(description = "物品定义主键")
    private Long itemId;

    /** 目录类别。 */
    @Schema(description = "目录类别")
    private String categoryCode;

    /** 库存行主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 库存行主键。 */
    @Schema(description = "库存行主键")
    private Long inventoryId;

    /** 预减后剩余量（小单位）。 */
    @Schema(description = "预减后剩余量（小单位）")
    private BigDecimal quantity;

    /** 小单位编码。 */
    @Schema(description = "小单位编码")
    private String unitCode;

    /** 小单位展示名。 */
    @Schema(description = "小单位展示名")
    private String unitName;

    /** 批号。 */
    @Schema(description = "批号")
    private String lotNumber;

    /** 效期截止时刻；空表示未维护。 */
    @Schema(description = "效期截止时刻")
    private LocalDateTime expirationDate;

    /** 库存行创建时间。 */
    @Schema(description = "库存行创建时间")
    private LocalDateTime createdAt;

    /** 库位 id。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 库位主键。 */
    @Schema(description = "库位主键")
    private Long locationStoreId;

    /** 库房 id。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 库房主键。 */
    @Schema(description = "库房主键")
    private Long locationId;

    /** 库房名称。 */
    @Schema(description = "库房名称")
    private String locationName;

    /**
     * 该库存行批号对应的批号售价（元），来源 {@code adm_charge_item_definition.price_value}，
     * 价型 {@code LOT_NUMBER_PRICE} 且 {@code lot_no} 与本行 {@link #lotNumber} 匹配。
     */
    @Schema(description = "批号售价（LOT_NUMBER_PRICE，随批号变化）")
    private BigDecimal price;

    /** 批号售价定价行主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "批号售价定价行主键")
    private Long chargeItemDefinitionId;

    @Schema(description = "展示顺序")
    private Integer displayOrder;
}
