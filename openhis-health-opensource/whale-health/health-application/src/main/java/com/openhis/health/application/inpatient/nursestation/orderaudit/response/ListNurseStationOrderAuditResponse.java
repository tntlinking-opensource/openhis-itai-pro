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

package com.openhis.health.application.inpatient.nursestation.orderaudit.response;

import com.openhis.health.application.inpatient.nursestation.orderaudit.dto.NurseStationOrderAuditPatientGroupDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 医嘱校对列表查询响应（IF-IP-AUD-01）。
 */
@Data
@Accessors(chain = true)
/** 医嘱校对列表查询响应。 */
@Schema(description = "医嘱校对列表查询响应")
public class ListNurseStationOrderAuditResponse {

    @Schema(description = "按 encounterId 分组的医嘱行")
    private List<NurseStationOrderAuditPatientGroupDto> groups;
}
