/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.surplusretention.request;

import com.zxplat.framework.core.application.dto.ExcelExportColumnDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 药品结余留用报表导出请求（IF-RPT-SRB-03）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "药品结余留用报表导出请求")
public class ExportSurplusRetentionReportRequest extends PageSurplusRetentionReportRequest {

    /** 前端可见列裁剪（field/title）；空则 AnnotatedExcelExporter 回落全部 @Excel 列。 */
    @Schema(description = "导出列")
    private List<ExcelExportColumnDto> exportColumns;
}
