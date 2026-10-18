/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.pharmacy.dispense.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 住院发药（核发）提交行（IF-IPP-05）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院发药提交行")
public class DispenseMedicationLineSubmitDto {

    /** 待发药发放行主键 {@code med_medication_dispense.id}；须处于 {@code IN_PROGRESS} 且库存/Charge 门禁通过。 */
    @Schema(description = "发放单主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long dispenseId;
}
