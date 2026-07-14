/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.pharmacy.dispense.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 保存配药提交行。
 */
@Data
@Accessors(chain = true)
@Schema(description = "保存配药提交行")
public class SavePrepareDispenseLineSubmitDto {

    /** 发放单主键。 */
    @NotNull(message = "发放单主键不能为空")
    @Schema(description = "发放单主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long dispenseId;

    /** 确认后的发放库房。 */
    @NotNull(message = "发放库房不能为空")
    /** 发放库房。 */
    @Schema(description = "发放库房（库位）主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long dispenseLocationId;

    /** 批号。 */
    @Schema(description = "确认批号")
    private String lotNumber;

    /** 追溯码（多码英文逗号拼接）。 */
    @Schema(description = "追溯码")
    private String traceNo;

    /** 实发数量。 */
    @NotNull(message = "实发数量不能为空")
    /** 实发数量。 */
    @Schema(description = "实发数量", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal quantity;
}
