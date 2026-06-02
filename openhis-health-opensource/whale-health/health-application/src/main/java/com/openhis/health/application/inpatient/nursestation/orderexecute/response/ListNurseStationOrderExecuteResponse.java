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

package com.openhis.health.application.inpatient.nursestation.orderexecute.response;

import com.openhis.health.application.inpatient.nursestation.orderexecute.dto.NurseStationOrderExecutePatientGroupDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 医嘱执行列表响应（IF-IP-EXE-01）。
 *
 * <p>{@code groups} 按 encounterId 分组；每组 {@code orders} 已按长临+开立时间排序，
 * 每行含四类槽位与 bindItems。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "医嘱执行列表响应")
public class ListNurseStationOrderExecuteResponse {

    /** 按就诊分组的医嘱行。 */
    @Schema(description = "按就诊分组的医嘱行")
    private List<NurseStationOrderExecutePatientGroupDto> groups;
}
