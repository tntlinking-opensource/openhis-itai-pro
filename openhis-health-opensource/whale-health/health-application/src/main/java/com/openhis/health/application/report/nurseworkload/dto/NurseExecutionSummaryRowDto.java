/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.nurseworkload.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 护士诊疗执行汇总行（IF-RPT-NWL-02）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "护士诊疗执行汇总行")
public class NurseExecutionSummaryRowDto {

    /** 诊疗类目码。 */
    @Schema(description = "诊疗类目码")
    private String categoryEnum;

    /** 诊疗类目展示名。 */
    @Schema(description = "诊疗类目展示名")
    private String categoryDisplay;

    /** 执行次数。 */
    @Schema(description = "执行次数")
    private Long executionCount;
}
