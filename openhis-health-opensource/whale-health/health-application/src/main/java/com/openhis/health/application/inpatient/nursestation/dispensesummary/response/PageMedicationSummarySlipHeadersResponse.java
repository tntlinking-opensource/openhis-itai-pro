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

package com.openhis.health.application.inpatient.nursestation.dispensesummary.response;

import com.openhis.health.application.inpatient.nursestation.dispensesummary.dto.NurseStationDispenseSummarySlipHeaderDto;
import com.openhis.whale.core.application.dto.PagedResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 分页查询汇总单头响应（IF-IP-SUM-02）。
 */
@Getter
@Setter
/** 分页查询汇总单头响应。 */
@Schema(description = "分页查询汇总单头响应")
public class PageMedicationSummarySlipHeadersResponse extends PagedResult<NurseStationDispenseSummarySlipHeaderDto> {

    @Schema(description = "汇总单头行")
    private List<NurseStationDispenseSummarySlipHeaderDto> rows;
}
