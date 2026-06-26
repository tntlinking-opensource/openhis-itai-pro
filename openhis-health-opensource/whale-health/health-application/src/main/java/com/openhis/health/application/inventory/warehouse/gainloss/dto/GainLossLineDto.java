/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inventory.warehouse.gainloss.dto;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Accessors(chain = true)
/** 药库损益单明细行。 */
@Schema(description = "药库损益单明细行（写）")
public class GainLossLineDto {

    /** 物品来源表名。 */
    @Schema(description = "物品来源表名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String itemTable;

    @JsonSerialize(using = ToStringSerializer.class)
    /** 物品定义主键。 */
    @Schema(description = "物品定义主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long itemId;

    /** 损益数量。 */
    @Schema(description = "损益数量（有符号拆零量）", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal gainLossQuantity;

    /** 计量单位编码。 */
    @Schema(description = "计量单位编码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String unitCode;

    /** 批号。 */
    @Schema(description = "批号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String lotNumber;

    /** 原因。 */
    @Schema(description = "原因")
    private String reason;

    /** 单价。 */
    @Schema(description = "单价")
    private BigDecimal price;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    /** 业务发生时间 occurrenceTime。 */
    @Schema(description = "业务发生时间 occurrenceTime")
    private LocalDateTime occurrenceTime;
}
