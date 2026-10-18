/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.doctorstation.diagnosis.response;

import com.zxplat.application.outpatient.doctorstation.diagnosis.dto.EncounterDiagnosisRowDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-DOR-06：就诊诊断列表响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站-就诊诊断列表响应")
public class EncounterDiagnosisResponse {

    @Schema(description = "就诊诊断行列表")
    /** 列表总数。 */
    private List<EncounterDiagnosisRowDto> diagnosisList;

    @Schema(description = "列表总数")
    private Integer totalCount;
}
