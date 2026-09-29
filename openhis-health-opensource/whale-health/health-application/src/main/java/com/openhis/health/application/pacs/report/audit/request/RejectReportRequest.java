/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.pacs.report.audit.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 审核退回请求。
 */
@Getter
@Setter
@Schema(description = "报告审核-退回请求")
public class RejectReportRequest {

    @NotNull
    @Schema(description = "报告主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long reportId;

    @NotBlank
    @Schema(description = "退回原因", requiredMode = Schema.RequiredMode.REQUIRED)
    private String reason;
}
