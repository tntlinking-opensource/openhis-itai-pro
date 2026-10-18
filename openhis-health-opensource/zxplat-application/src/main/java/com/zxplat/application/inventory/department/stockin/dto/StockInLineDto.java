/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.department.stockin.dto;

import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 科室入库单写模型明细行（IF-DEPT-IN-04/05）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "科室入库单明细行（写）")
public class StockInLineDto {

    /** 物品来源表名。 */
    @Schema(description = "物品来源表名")
    private String itemTable;

    @JsonSerialize(using = ToStringSerializer.class)
    /** 物品定义主键。 */
    @Schema(description = "物品定义主键")
    private Long itemId;

    /**
     * 来源药库中已选定的 {@code wkf_inventory_item.id}。
     * <p>前端从选品带回时，草稿校验走「行级可用量」路径，与药库出库一致。</p>
     * <p>可空：未绑定时后端按「物品 + 归一化批号」在内存聚合后与可用目录行比对。</p>
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "来源药库库存行主键；有值时按行校验可用量，无值时按品+批号聚合")
    /** 来源药库库存行主键；有值时按行校验可用量，无值时按品+批号聚合。 */
    private Long inventoryItemId;

    /** 数量。 */
    @Schema(description = "数量")
    private BigDecimal itemQuantity;

    /** 计量单位编码。 */
    @Schema(description = "计量单位编码")
    private String unitCode;

    /** 批号。 */
    @Schema(description = "批号")
    private String lotNumber;

    /** 追溯码。 */
    @Schema(description = "追溯码")
    private String traceNo;

    /** 单价。 */
    @Schema(description = "单价")
    private BigDecimal price;

    /** 行总价。 */
    @Schema(description = "行总价")
    private BigDecimal totalPrice;

    /** 行备注。 */
    @Schema(description = "行备注")
    private String remake;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    /** 业务发生时间。 */
    @Schema(description = "业务发生时间")
    private LocalDateTime occurrenceTime;
}
