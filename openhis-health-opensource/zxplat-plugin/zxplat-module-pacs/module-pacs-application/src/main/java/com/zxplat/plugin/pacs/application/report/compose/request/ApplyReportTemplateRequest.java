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
 * 套用报告模板请求（IF-PACS-REP-04）。
 */
@Getter
@Setter
@Schema(description = "报告书写-套用模板请求")
public class ApplyReportTemplateRequest {

    /** 报告主键。 */
    @NotNull
    @Schema(description = "报告主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long reportId;

    /** 模板主键。 */
    @NotNull
    @Schema(description = "模板主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long templateId;
}
