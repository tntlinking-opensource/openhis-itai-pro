/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.keymedicationusage.request;

import com.zxplat.framework.core.application.dto.ExcelExportColumnDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 重点药品使用统计导出请求（IF-RPT-KDU-03；筛选字段与汇总同构）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "重点药品使用统计导出请求")
public class ExportKeyMedicationUsageReportRequest extends SummarizeKeyMedicationUsageReportRequest {

    /**
     * 前端下发的导出列；空则导出全部 {@code @Excel} 列。
     */
    @Schema(description = "导出列清单（页面可见列）")
    private List<ExcelExportColumnDto> exportColumns;
}
