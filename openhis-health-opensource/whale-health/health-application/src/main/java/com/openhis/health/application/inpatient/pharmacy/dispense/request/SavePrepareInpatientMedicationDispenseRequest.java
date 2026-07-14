/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.pharmacy.dispense.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.inpatient.pharmacy.dispense.dto.SavePrepareInpatientDispenseLineSubmitDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 住院保存配药请求（IF-IPP-04）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院保存配药请求")
public class SavePrepareInpatientMedicationDispenseRequest {

    /** 住院就诊主键；配药行须全部归属同一 encounter。 */
    @Schema(description = "就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 调配药师主键。 */
    @Schema(description = "调配药师主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long preparerPractitionerId;

    /** 配药行。 */
    @Schema(description = "配药行", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<SavePrepareInpatientDispenseLineSubmitDto> lines;
}
