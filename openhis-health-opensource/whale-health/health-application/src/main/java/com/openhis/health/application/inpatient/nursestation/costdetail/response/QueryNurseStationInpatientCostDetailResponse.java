/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.costdetail.response;

import com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto.NurseStationCostDetailLineDto;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto.NurseStationCostDetailTotalsDto;
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

    @Schema(description = "展示模式")
    /** 展示模式。 */
    private String presentationMode;

    @Schema(description = "明细行列表")
    /** 明细行列表。 */
    private List<NurseStationCostDetailLineDto> detailLines;

    /** 汇总行列表。 */
    @Schema(description = "汇总行列表")
    private List<NurseStationCostSummaryLineDto> summaryLines;

    /** 合计块。 */
    @Schema(description = "合计块")
    private NurseStationCostDetailTotalsDto totals;
}
