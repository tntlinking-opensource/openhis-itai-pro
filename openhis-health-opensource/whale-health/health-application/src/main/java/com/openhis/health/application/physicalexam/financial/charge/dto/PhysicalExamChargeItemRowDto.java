/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.physicalexam.financial.charge.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 体检收费明细行 DTO。
 */
@Data
@Accessors(chain = true)
@Schema(description = "体检收费明细行")
public class PhysicalExamChargeItemRowDto {

    /** 收费明细主键。 */
    @Schema(description = "收费明细主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long chargeItemId;

    /** 收费状态 code。 */
    @Schema(description = "收费状态 code")
    private String status;

    /** 项目展示名称。 */
    @Schema(description = "项目展示名称")
    private String itemLabel;

    /** 行总价。 */
    @Schema(description = "行总价")
    private BigDecimal totalPrice;

    /** 单价。 */
    @Schema(description = "单价")
    private BigDecimal unitPrice;

    /** 数量。 */
    @Schema(description = "数量")
    private BigDecimal quantity;
}
