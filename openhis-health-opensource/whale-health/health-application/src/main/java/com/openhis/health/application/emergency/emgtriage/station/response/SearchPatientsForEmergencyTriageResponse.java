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

package com.openhis.health.application.emergency.emgtriage.station.response;

import com.openhis.health.application.emergency.emgtriage.station.dto.PatientAlertRiskDto;
import com.openhis.health.domain.administration.patient.queries.model.PatientPageItemQueryModel;
import com.openhis.whale.core.application.dto.PagedResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * IF-EMG-02：患者检索响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "急诊分诊-患者检索响应")
public class SearchPatientsForEmergencyTriageResponse {

    @Schema(description = "患者分页结果")
    private PagedResult<PatientPageItemQueryModel> patientPage;

    @Schema(description = "警示摘要（PatientAlert 未接入时为空列表）")
    private List<PatientAlertRiskDto> alerts = new ArrayList<>();
}
