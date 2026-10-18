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
 * 进入报告编辑请求（IF-PACS-REP-01）。
 */
@Getter
@Setter
@Schema(description = "报告书写-进入编辑请求")
public class OpenComposeRequest {

    @NotNull
    @Schema(description = "工单主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long workItemId;
}
