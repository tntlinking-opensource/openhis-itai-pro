/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.pacs.integration.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 报告路径回传响应。
 */
@Getter
@Setter
@Schema(description = "报告路径回传响应")
public class NotifyReportPathResponse {

    @Schema(description = "报告主键")
    private Long reportId;

    @Schema(description = "是否回传成功")
    private Boolean success;

    @Schema(description = "PDF 存储键")
    private String pdfStorageKey;

    @Schema(description = "说明（adapter 未启用时）")
    private String message;
}
