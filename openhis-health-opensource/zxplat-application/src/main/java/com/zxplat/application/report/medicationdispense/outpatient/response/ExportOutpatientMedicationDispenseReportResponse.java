/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.medicationdispense.outpatient.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 门诊发药报表导出响应（IF-RPT-OMD-04）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊发药报表导出响应")
public class ExportOutpatientMedicationDispenseReportResponse {

    /** 默认文件名。 */
    @Schema(description = "文件名")
    private String fileName;

    /** xlsx MIME。 */
    @Schema(description = "内容类型")
    private String contentType;

    /** 文件字节流。 */
    @Schema(description = "文件字节")
    private byte[] body;

    /** 导出行数。 */
    @Schema(description = "导出行数")
    private Integer exportedRowCount;

    /** 是否触达导出行上限。 */
    @Schema(description = "是否截断")
    private Boolean truncated;
}
