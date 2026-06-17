/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.outpatient.doctorstation.diagnosis.response;

import com.openhis.health.application.outpatient.doctorstation.diagnosis.dto.EncounterDiagnosisRowDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-DOR-06：就诊诊断列表响应。
 */
@Data
@Accessors(chain = true)
/** 医生站-就诊诊断列表响应。 */
@Schema(description = "医生站-就诊诊断列表响应")
public class EncounterDiagnosisResponse {

    @Schema(description = "就诊诊断行列表")
    private List<EncounterDiagnosisRowDto> diagnosisList;

    @Schema(description = "列表总数")
    private Integer totalCount;
}
