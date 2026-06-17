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
import com.openhis.health.application.inpatient.nursestation.dispensesummary.dto.NurseStationDispenseSummarySlipLineDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 查询汇总单合并明细响应（IF-IP-SUM-03）。
 */
@Getter
@Setter
@Accessors(chain = true)
/** 查询汇总单合并明细响应。 */
@Schema(description = "查询汇总单合并明细响应")
public class GetMedicationSummarySlipDetailResponse {

    @Schema(description = "汇总单号")
    private String summaryNo;

    /** 单头。 */
    @Schema(description = "单头")
    private NurseStationDispenseSummarySlipHeaderDto header;

    /** 合并明细（无批号）。 */
    @Schema(description = "合并明细")
    private List<NurseStationDispenseSummarySlipLineDto> lines;
}
