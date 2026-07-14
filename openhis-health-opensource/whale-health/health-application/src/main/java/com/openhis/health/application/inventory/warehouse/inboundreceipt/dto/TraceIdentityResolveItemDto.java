/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.inboundreceipt.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 批量扫码身份反查成功项（IF-TRC-21）。
 */
@Getter
@Setter
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

    @JsonSerialize(using = ToStringSerializer.class)
    /** 品项标识。 */
    @Schema(description = "品项标识")
    private Long itemId;

    /** 批号。 */
    @Schema(description = "批号")
    private String lotNumber;

    /** 追溯码单位。 */
    @Schema(description = "追溯码单位")
    private String traceUnitCode;

    /** 拆零剩余量。 */
    @Schema(description = "拆零剩余量")
    private BigDecimal remainingSplitQuantity;

    /** 在院状态。 */
    @Schema(description = "在院状态")
    private String inHospitalStatusEnum;
}
