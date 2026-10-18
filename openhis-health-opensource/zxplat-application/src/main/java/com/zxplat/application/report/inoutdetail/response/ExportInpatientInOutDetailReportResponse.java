/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.inoutdetail.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 住院患者入出明细报表导出响应（IF-RPT-IOE-03）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院患者入出明细报表导出响应")
public class ExportInpatientInOutDetailReportResponse {

    /** 建议下载文件名。 */
    @Schema(description = "文件名")
    private String fileName;

    /** xlsx MIME 类型。 */
    @Schema(description = "内容类型")
    private String contentType;

    /** xlsx 二进制内容。 */
    @Schema(description = "文件字节")
    private byte[] body;

    /** 导出行数。 */
    @Schema(description = "导出行数")
    private Integer rowCount;

    /** 是否触达导出行上限而截断。 */
    @Schema(description = "是否截断")
    private Boolean truncated;

    /** 导出行中是否含转科信息（与列表动态列同精神）。 */
    @Schema(description = "是否展示转科列")
    private Boolean showTransferColumn;
}
