/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.application.report.audit.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 审核退回响应。
 */
@Getter
@Setter
@Schema(description = "报告审核-退回响应")
public class RejectReportResponse {

    @Schema(description = "报告主键")
    private Long reportId;

    @Schema(description = "状态")
    private String statusEnum;

    @Schema(description = "是否成功")
    private Boolean success;
}
