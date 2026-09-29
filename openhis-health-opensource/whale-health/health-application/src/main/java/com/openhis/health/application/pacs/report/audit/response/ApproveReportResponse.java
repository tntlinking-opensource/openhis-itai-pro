/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.pacs.report.audit.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 审核通过响应。
 */
@Getter
@Setter
@Schema(description = "报告审核-通过响应")
public class ApproveReportResponse {

    @Schema(description = "报告主键")
    private Long reportId;

    @Schema(description = "状态")
    private String statusEnum;

    @Schema(description = "发布时间")
    private LocalDateTime issuedAt;

    @Schema(description = "读侧索引是否已写入")
    private Boolean indexUpserted;
}
