/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.surplusretention.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 药品结余留用报表导出响应（IF-RPT-SRB-03）。
 */
@Data
@Schema(description = "药品结余留用报表导出响应")
public class ExportSurplusRetentionReportResponse {

    /** 下载文件名（含日期窗后缀）。 */
    @Schema(description = "文件名")
    private String fileName;

    /** xlsx 文件字节内容。 */
    @Schema(description = "文件内容")
    private byte[] content;

    /** 实际写入 Excel 的行数（触顶截断后为上限行数）。 */
    @Schema(description = "行数")
    private Integer rowCount;

    /** 是否因 EXPORT_MAX_ROWS 触顶截断（前端须提示）。 */
    @Schema(description = "是否截断")
    private Boolean truncated;

    /** MIME 类型（xlsx）。 */
    @Schema(description = "内容类型")
    private String contentType;
}
