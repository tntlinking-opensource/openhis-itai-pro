/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.application.integration.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 报告路径回传 HIS 请求（IF-PACS-INT-06 写侧）。
 */
@Getter
@Setter
@Schema(description = "报告路径回传 HIS 请求")
public class NotifyReportPathRequest {

    @NotNull
    @Schema(description = "报告主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long reportId;
}
