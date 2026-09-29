/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.pacs.report.compose.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 提交报告响应。
 */
@Getter
@Setter
@Schema(description = "报告书写-提交响应")
public class SubmitReportResponse {

    @Schema(description = "报告主键")
    private Long reportId;

    @Schema(description = "状态")
    private String statusEnum;

    @Schema(description = "提交时间")
    private LocalDateTime submittedAt;

    @Schema(description = "是否待审")
    private Boolean pendingAudit;
}
