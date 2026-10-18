/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.practitionerworkload.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 导出科室/医生工作量明细响应（IF-RPT-WKL-04）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "导出科室医生工作量明细响应")
public class ExportPractitionerWorkloadDetailResponse {

    /** 文件名。 */
    @Schema(description = "文件名")
    private String fileName;

    /** Content-Type。 */
    @Schema(description = "Content-Type")
    private String contentType;

    /** 文件内容。 */
    @Schema(description = "文件内容")
    private byte[] body;

    /** 导出行数。 */
    @Schema(description = "导出行数")
    private Integer rowCount;

    /** 是否截断。 */
    @Schema(description = "是否截断")
    private Boolean truncated;
}
