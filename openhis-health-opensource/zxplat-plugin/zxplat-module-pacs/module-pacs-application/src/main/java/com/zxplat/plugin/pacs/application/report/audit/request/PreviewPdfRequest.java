/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.application.report.audit.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * PDF 预览请求。
 */
@Getter
@Setter
@Schema(description = "报告审核-PDF 预览请求")
public class PreviewPdfRequest {

    @NotNull
    @Schema(description = "报告主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long reportId;
}
