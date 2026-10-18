/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.application.report.audit.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 取消审核请求（IF-PACS-REP-09）。
 */
@Getter
@Setter
@Schema(description = "报告审核-取消审核请求")
public class CancelAuditRequest {

    /** 已发布报告主键。 */
    @NotNull
    @Schema(description = "报告主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long reportId;

    /** 取消原因（必填）。 */
    @NotBlank
    @Schema(description = "取消原因", requiredMode = Schema.RequiredMode.REQUIRED)
    private String reason;
}
