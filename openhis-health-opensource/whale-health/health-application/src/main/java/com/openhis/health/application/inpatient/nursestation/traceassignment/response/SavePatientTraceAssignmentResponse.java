/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.traceassignment.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-IP-TRC-04：保存患者追溯码分配出参。
 */
@Data
@Accessors(chain = true)
@Schema(description = "保存患者追溯码分配出参")
public class SavePatientTraceAssignmentResponse {

    /** 成功保存的汇总组数。 */
    @Schema(description = "成功保存的汇总组数")
    private Integer savedGroupCount;
}
