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

package com.openhis.health.application.inpatient.nursestation.inpatientbilling.response;

import com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto.NurseStationAutoRollConfigRowDto;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto.NurseStationAutoRollNursingReadonlyDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * IF-IP-ARG-02：自动计费配置列表响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "自动计费配置列表响应")
public class ListAutoRollConfigsResponse {

    /** 配置列表（含 INACTIVE）。 */
    @Schema(description = "配置列表")
    private List<NurseStationAutoRollConfigRowDto> configs = new ArrayList<>();

    /** 护理级别只读区；无有效医嘱时为 null。 */
    @Schema(description = "护理级别只读区")
    private NurseStationAutoRollNursingReadonlyDto nursingLevelReadonly;
}
