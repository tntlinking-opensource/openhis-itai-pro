/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.encountermanage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 单就诊维度的护士站任务计数（BR-IP-NUR-004/005）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "护士站任务计数")
public class NurseStationTaskCountBundle {

    @Schema(description = "待校对条数")
    private Integer pendingAuditCount;

    @Schema(description = "待执行条数")
    private Integer pendingExecuteCount;
}
