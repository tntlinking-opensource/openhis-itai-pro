/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.feedetail.inpatient.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 住院费用明细报表导出响应（IF-RPT-IFD-04）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院费用明细报表导出响应")
public class ExportInpatientFeeDetailReportResponse {

    /** 建议下载文件名。 */
    @Schema(description = "文件名")
    private String fileName;

    /** xlsx MIME 类型。 */
    @Schema(description = "内容类型")
    private String contentType;

    /** xlsx 二进制内容。 */
    @Schema(description = "文件字节")
    private byte[] body;

    /** 导出的行数。 */
    @Schema(description = "导出行数")
    private Integer rowCount;

    /** 是否触达导出行上限而截断。 */
    @Schema(description = "是否截断")
    private Boolean truncated;
}
