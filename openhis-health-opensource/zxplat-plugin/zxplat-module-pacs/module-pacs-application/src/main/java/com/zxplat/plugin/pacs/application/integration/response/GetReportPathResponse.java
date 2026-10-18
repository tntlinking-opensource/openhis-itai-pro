/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.application.integration.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 报告路径查询响应。
 */
@Getter
@Setter
@Schema(description = "报告路径查询响应")
public class GetReportPathResponse {

    @Schema(description = "报告主键")
    private Long reportId;

    @Schema(description = "工单主键")
    private Long workItemId;

    @Schema(description = "检查号")
    private String accessionNumber;

    @Schema(description = "报告状态")
    private String statusEnum;

    @Schema(description = "PDF 对象存储键")
    private String pdfStorageKey;
}
