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

/**
 * 核发（发药）提交行。
 */
@Data
@Accessors(chain = true)
@Schema(description = "核发提交行")
public class DispenseMedicationLineSubmitDto {

    /** 药品发放单主键 {@code med_medication_dispense.id}。 */
    @NotNull(message = "发放单主键不能为空")
    @Schema(description = "发放单主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long dispenseId;

    /**
     * 核发前最终追溯码（逗号串；可空）。
     *
     * <p>已配药重试时前端可能已改扫码；有值则覆盖发放行 {@code trace_no} 后再扣主档，
     * 避免界面码与落库扣减码不一致。</p>
     */
    @Schema(description = "核发前最终追溯码（逗号串；可空）")
    private String traceNo;
}
