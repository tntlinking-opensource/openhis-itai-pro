/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.pharmacy.dispense.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 单码身份反查成功项（IF-TRC-21）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "追溯码身份反查成功项")
public class TraceIdentityResolveItemDto {

    /** 追溯码。 */
    @Schema(description = "追溯码")
    private String traceNo;

    /** 解析状态。 */
    @Schema(description = "解析状态")
    private String resolveStatusEnum;

    /** 品项表。 */
    @Schema(description = "品项表")
    private String itemTable;

    /** 药品定义标识。 */
    @Schema(description = "药品定义标识")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long itemId;

    /** 批号。 */
    @Schema(description = "批号")
    private String lotNumber;

    /** 追溯码单位（主档）。 */
    @Schema(description = "追溯码单位")
    private String traceUnitCode;

    /** 主档贴码比例快照（1 码对应拆零量）。 */
    @Schema(description = "主档贴码比例快照")
    private BigDecimal traceUnitRatio;

    /** 拆零剩余量。 */
    @Schema(description = "拆零剩余量")
    private BigDecimal remainingSplitQuantity;

    /** 在院状态。 */
    @Schema(description = "在院状态")
    private String inHospitalStatusEnum;
}
