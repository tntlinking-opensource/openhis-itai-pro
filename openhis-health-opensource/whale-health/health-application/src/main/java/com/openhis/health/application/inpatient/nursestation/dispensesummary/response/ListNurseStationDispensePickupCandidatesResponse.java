/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inpatient.nursestation.dispensesummary.response;

import com.openhis.health.application.inpatient.nursestation.dispensesummary.dto.NurseStationDispensePickupLineDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
/** 汇总领药候选明细响应。 */
@Schema(description = "汇总领药候选明细响应")
public class ListNurseStationDispensePickupCandidatesResponse {

    /** 候选明细。 */
    @Schema(description = "候选明细")
    private List<NurseStationDispensePickupLineDto> lines;
}
