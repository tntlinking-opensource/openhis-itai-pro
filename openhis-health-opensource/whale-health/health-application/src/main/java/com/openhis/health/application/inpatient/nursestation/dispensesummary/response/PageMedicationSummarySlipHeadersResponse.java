/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
    /** 汇总单头行。 */
    private List<NurseStationDispenseSummarySlipHeaderDto> rows;
}
