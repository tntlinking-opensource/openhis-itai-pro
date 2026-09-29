/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.pacs.report.audit.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * PDF 预览响应（返回存储键，不下载字节）。
 */
@Getter
@Setter
@Schema(description = "报告审核-PDF 预览响应")
public class PreviewPdfResponse {

    @Schema(description = "报告主键")
    private Long reportId;

    @Schema(description = "PDF 对象存储键")
    private String pdfStorageKey;
}
