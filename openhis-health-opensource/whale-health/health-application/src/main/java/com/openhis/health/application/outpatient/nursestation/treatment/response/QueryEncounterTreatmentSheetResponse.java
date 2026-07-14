/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.nursestation.treatment.response;

import com.openhis.health.application.outpatient.nursestation.treatment.dto.NurseStationTreatmentLineDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 按就诊查询处置单（三列表同构）响应（IF-NUR-02）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "按就诊查询处置单响应")
public class QueryEncounterTreatmentSheetResponse {

    /**
     * 诊疗申请行列表。
     */
    @Schema(description = "诊疗申请行列表")
    private List<NurseStationTreatmentLineDto> serviceLines;

    /**
     * 药品申请行列表。
     */
    @Schema(description = "药品申请行列表")
    private List<NurseStationTreatmentLineDto> medicationLines;

    /**
     * 耗材相关行列表。
     */
    @Schema(description = "耗材相关行列表")
    private List<NurseStationTreatmentLineDto> deviceLines;
}
