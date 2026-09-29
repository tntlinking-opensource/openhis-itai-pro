/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.pacs.report.compose.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 保存报告 PDF 存储键请求（IF-PACS-REP-05）。
 */
@Getter
@Setter
@Schema(description = "报告书写-保存 PDF 键请求")
public class SaveReportPdfRequest {

    @NotNull
    @Schema(description = "报告主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long reportId;

    @NotBlank
    @Schema(description = "PDF 对象存储键", requiredMode = Schema.RequiredMode.REQUIRED)
    private String pdfStorageKey;
}
