/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.pacs.report.compose.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 保存报告草稿请求（IF-PACS-REP-02）。
 */
@Getter
@Setter
@Schema(description = "报告书写-保存草稿请求")
public class SaveReportDraftRequest {

    @NotNull
    @Schema(description = "报告主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long reportId;

    @Schema(description = "所见")
    private String findings;

    @Schema(description = "结论")
    private String conclusion;
}
