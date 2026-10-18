/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.consumableusage.inpatient.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 住院耗材使用报表导出响应（IF-RPT-ICU-04）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院耗材使用报表导出响应")
public class ExportInpatientConsumableUsageReportResponse {

    /** 默认文件名。 */
    @Schema(description = "文件名")
    private String fileName;

    /** xlsx MIME。 */
    @Schema(description = "内容类型")
    private String contentType;

    /** 文件字节。 */
    @Schema(description = "文件字节")
    private byte[] body;

    /** 导出行数。 */
    @Schema(description = "导出行数")
    private Integer exportedRowCount;

    /** 是否触达导出行上限。 */
    @Schema(description = "是否截断")
    private Boolean truncated;
}
