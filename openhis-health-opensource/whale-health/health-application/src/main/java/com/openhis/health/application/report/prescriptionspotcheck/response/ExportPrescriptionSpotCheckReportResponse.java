/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.prescriptionspotcheck.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 门诊处方抽查导出响应（IF-RPT-PSC-03）。
 */
@Data
@Schema(description = "门诊处方抽查导出响应")
public class ExportPrescriptionSpotCheckReportResponse {

    /** 下载文件名。 */
    @Schema(description = "文件名")
    private String fileName;

    /** xlsx 字节。 */
    @Schema(description = "文件内容")
    private byte[] content;

    /** 实际导出行数。 */
    @Schema(description = "行数")
    private Integer rowCount;

    /** 是否因上限截断。 */
    @Schema(description = "是否截断")
    private Boolean truncated;

    /** MIME 类型。 */
    @Schema(description = "内容类型")
    private String contentType;
}
