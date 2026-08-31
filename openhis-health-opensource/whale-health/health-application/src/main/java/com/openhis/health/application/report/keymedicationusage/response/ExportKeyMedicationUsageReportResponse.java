/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.keymedicationusage.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 重点药品使用统计导出响应（IF-RPT-KDU-03）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "重点药品使用统计导出响应")
public class ExportKeyMedicationUsageReportResponse {

    /**
     * 建议下载文件名。
     */
    @Schema(description = "文件名")
    private String fileName;

    /**
     * xlsx MIME 类型。
     */
    @Schema(description = "内容类型")
    private String contentType;

    /**
     * xlsx 二进制内容。
     */
    @Schema(description = "文件字节")
    private byte[] body;

    /**
     * 导出行数。
     */
    @Schema(description = "导出行数")
    private Integer rowCount;

    /**
     * 是否截断（本报表矩阵 ≤6 行，恒为 false）。
     */
    @Schema(description = "是否截断")
    private Boolean truncated;
}
