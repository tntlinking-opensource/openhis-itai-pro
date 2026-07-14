/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.dispensesummary.response;

import com.openhis.health.application.inpatient.nursestation.dispensesummary.dto.NurseStationDispensePickupLineDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 汇总领药候选明细响应（护士站勾选池查询；IF-IP-SUM 同族）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "汇总领药候选明细响应")
public class ListNurseStationDispensePickupCandidatesResponse {

    /** 候选明细。 */
    @Schema(description = "候选明细")
    private List<NurseStationDispensePickupLineDto> lines;
}
