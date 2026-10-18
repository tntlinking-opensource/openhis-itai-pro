/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.nurseworkload.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 护士工作量明细导出出参（IF-RPT-NWL-06）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "护士工作量明细导出 IF-RPT-NWL-06")
public class ExportNurseWorkloadDetailResponse {

    /** 文件名。 */
    @Schema(description = "文件名")
    private String fileName;

    /** 内容类型。 */
    @Schema(description = "内容类型")
    private String contentType;

    /** 文件字节。 */
    @Schema(description = "文件字节")
    private byte[] fileBytes;

    /** 是否截断。 */
    @Schema(description = "是否截断")
    private Boolean truncated;

    /** 导出行数。 */
    @Schema(description = "导出行数")
    private Integer exportedRowCount;

    /** 主 Tab 码。 */
    @Schema(description = "主 Tab 码")
    private String mainTabCode;
}
