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

package com.openhis.health.application.inpatient.nursestation.costdetail.response;

import com.openhis.health.application.inpatient.nursestation.costdetail.dto.NurseStationCostDetailLineDto;
import com.openhis.health.application.inpatient.nursestation.costdetail.dto.NurseStationCostDetailTotalsDto;
import com.openhis.health.application.inpatient.nursestation.costdetail.dto.NurseStationCostSummaryLineDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-IP-CDT-02：查询住院费用明细/汇总响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院护士站费用明细查询响应")
public class QueryNurseStationInpatientCostDetailResponse {

    /** 回显请求展示模式。 */
    @Schema(description = "展示模式")
    private String presentationMode;

    /** 明细行；SUMMARY 模式为空列表。 */
    @Schema(description = "明细行列表")
    private List<NurseStationCostDetailLineDto> detailLines;

    /** 汇总行；DETAIL 模式为空列表。 */
    @Schema(description = "汇总行列表")
    private List<NurseStationCostSummaryLineDto> summaryLines;

    /** 合计块（两种模式均返回）。 */
    @Schema(description = "合计块")
    private NurseStationCostDetailTotalsDto totals;
}
