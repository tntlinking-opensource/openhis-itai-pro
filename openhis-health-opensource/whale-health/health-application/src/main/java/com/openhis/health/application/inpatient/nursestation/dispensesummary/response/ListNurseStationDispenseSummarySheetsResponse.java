/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inpatient.nursestation.dispensesummary.response;

import com.openhis.health.application.inpatient.nursestation.dispensesummary.dto.NurseStationDispenseSummarySheetDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
/** 汇总领药单列表响应。 */
@Schema(description = "汇总领药单列表响应")
public class ListNurseStationDispenseSummarySheetsResponse {

    /** 汇总单列表。 */
    @Schema(description = "汇总单列表")
    private List<NurseStationDispenseSummarySheetDto> sheets;
}
