/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.dispensesummary.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
/** 查询汇总领药单列表。 */
@Schema(description = "查询汇总领药单列表")
public class ListNurseStationDispenseSummarySheetsRequest {

    /** 就诊主键列表（可选，用于收窄范围）。 */
    @Schema(description = "就诊主键列表（可选，用于收窄范围）")
    private List<Long> encounterIds;

    /** 截止时间（含）。 */
    @Schema(description = "截止时间（含）")
    private LocalDateTime queryDeadline;
}
