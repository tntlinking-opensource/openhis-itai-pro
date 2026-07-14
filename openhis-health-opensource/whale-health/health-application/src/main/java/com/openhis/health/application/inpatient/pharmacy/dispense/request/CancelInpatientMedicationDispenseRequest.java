/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.pharmacy.dispense.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.inpatient.pharmacy.dispense.dto.CancelMedicationDispenseLineSubmitDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 住院作废发放请求（IF-IPP-06）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院作废发放请求")
public class CancelInpatientMedicationDispenseRequest {

    /** 住院就诊主键；作废行须全部归属同一 encounter。 */
    @Schema(description = "就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 作废行。 */
    @Schema(description = "作废行", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<CancelMedicationDispenseLineSubmitDto> lines;
}
