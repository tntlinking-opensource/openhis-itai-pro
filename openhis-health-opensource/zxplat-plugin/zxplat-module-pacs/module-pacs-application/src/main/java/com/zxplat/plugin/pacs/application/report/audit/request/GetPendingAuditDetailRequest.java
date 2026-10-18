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
 * 待审详情请求。
 */
@Getter
@Setter
@Schema(description = "报告审核-待审详情请求")
public class GetPendingAuditDetailRequest {

    @NotNull
    @Schema(description = "报告主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long reportId;
}
