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

package com.openhis.health.application.outpatient.nursestation.response;

import com.openhis.health.application.outpatient.nursestation.dto.NurseStationTreatmentLineDto;
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
