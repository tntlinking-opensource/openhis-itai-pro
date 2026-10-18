/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.application.report.compose.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 提交报告请求（IF-PACS-REP-03）。
 */
@Getter
@Setter
@Schema(description = "报告书写-提交请求")
public class SubmitReportRequest {

    @NotNull
    @Schema(description = "报告主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long reportId;
}
