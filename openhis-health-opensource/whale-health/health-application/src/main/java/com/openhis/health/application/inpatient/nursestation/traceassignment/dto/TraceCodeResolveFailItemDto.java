/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.traceassignment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-IP-TRC-03：追溯码解析失败明细。
 */
@Data
@Accessors(chain = true)
@Schema(description = "追溯码解析失败明细")
public class TraceCodeResolveFailItemDto {

    /** 原追溯码。 */
    @Schema(description = "原追溯码")
    private String traceCode;

    /** 失败原因码。 */
    @Schema(description = "失败原因码")
    private String reasonCode;
}
