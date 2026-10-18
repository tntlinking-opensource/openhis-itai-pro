/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.dispensesummary.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
/** 查询汇总领药候选明细。 */
@Schema(description = "查询汇总领药候选明细")
public class ListNurseStationDispensePickupCandidatesRequest {

    /** 就诊主键列表。 */
    @Schema(description = "就诊主键列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> encounterIds;

    /** 截止时间（含）。 */
    @Schema(description = "截止时间（含）")
    private LocalDateTime queryDeadline;
}
