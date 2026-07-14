/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.pharmacy.dispense.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 作废发放提交行。
 */
@Data
@Accessors(chain = true)
@Schema(description = "作废发放提交行")
public class CancelMedicationDispenseLineSubmitDto {

    /** 药品发放单主键 {@code med_medication_dispense.id}。 */
    @NotNull(message = "发放单主键不能为空")
    @Schema(description = "发放单主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long dispenseId;

    /** 未发药原因枚举 code。 */
    @NotBlank(message = "作废原因不能为空")
    /** 未发药原因枚举 code。 */
    @Schema(description = "未发药原因枚举 code", requiredMode = Schema.RequiredMode.REQUIRED)
    private String notPerformedReasonEnum;
}
