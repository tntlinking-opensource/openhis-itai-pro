/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.pharmacy.dispense.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 住院作废发放提交行（IF-IPP-06）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院作废发放提交行")
public class CancelMedicationDispenseLineSubmitDto {

    /** 待作废发放行主键；仅 {@code PREPARATION}/{@code IN_PROGRESS} 可推进 DECLINED。 */
    @Schema(description = "发放单主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long dispenseId;

    /** 未执行原因 code（落库 {@code not_performed_reason_enum}）。 */
    @Schema(description = "作废原因 code", requiredMode = Schema.RequiredMode.REQUIRED)
    private String notPerformedReasonEnum;
}
