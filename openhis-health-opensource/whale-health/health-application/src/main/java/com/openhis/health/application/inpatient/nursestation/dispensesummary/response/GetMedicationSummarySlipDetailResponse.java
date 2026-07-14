/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
@Schema(description = "查询汇总单合并明细响应")
public class GetMedicationSummarySlipDetailResponse {

    @Schema(description = "汇总单号")
    /** 汇总单号。 */
    private String summaryNo;

    /** 单头。 */
    @Schema(description = "单头")
    private NurseStationDispenseSummarySlipHeaderDto header;

    /** 合并明细。 */
    @Schema(description = "合并明细")
    private List<NurseStationDispenseSummarySlipLineDto> lines;
}
