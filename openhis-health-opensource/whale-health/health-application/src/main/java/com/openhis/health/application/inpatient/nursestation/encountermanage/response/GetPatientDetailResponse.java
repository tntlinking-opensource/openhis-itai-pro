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

package com.openhis.health.application.inpatient.nursestation.encountermanage.response;

import com.openhis.health.application.inpatient.nursestation.encountermanage.dto.PatientDetailDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 患者详情查询响应（IF-IP-NUR-03）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "患者详情查询响应")
public class GetPatientDetailResponse {

    /** 全景详情。 */
    @Schema(description = "患者全景详情")
    private PatientDetailDto detail;
}
