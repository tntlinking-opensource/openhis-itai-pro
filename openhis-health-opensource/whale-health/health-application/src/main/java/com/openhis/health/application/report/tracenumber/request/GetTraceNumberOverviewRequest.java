/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.tracenumber.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * IF-TRC-RPT-03：单码概览入参。
 */
@Getter
@Setter
@Schema(description = "追溯码概览 IF-TRC-RPT-03")
public class GetTraceNumberOverviewRequest {

    /** 追溯码。 */
    @NotBlank
    @Schema(description = "追溯码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String traceNo;
}
