/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.dispensesummary.response;

import com.zxplat.application.inpatient.nursestation.dispensesummary.dto.NurseStationDispenseSummarySheetDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 汇总领药单列表响应（护士站已提交汇总单分页/列表）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "汇总领药单列表响应")
public class ListNurseStationDispenseSummarySheetsResponse {

    /** 汇总单列表。 */
    @Schema(description = "汇总单列表")
    private List<NurseStationDispenseSummarySheetDto> sheets;
}
