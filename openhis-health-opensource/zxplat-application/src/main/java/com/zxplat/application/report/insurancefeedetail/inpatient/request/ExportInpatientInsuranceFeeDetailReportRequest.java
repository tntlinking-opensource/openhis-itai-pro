/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.insurancefeedetail.inpatient.request;

import com.zxplat.framework.core.application.dto.ExcelExportColumnDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 住院医保费用明细导出请求（IF-RPT-IIF-03）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "住院医保费用明细导出请求")
public class ExportInpatientInsuranceFeeDetailReportRequest
    extends PageInpatientInsuranceFeeDetailReportRequest {

    /** 前端可见列裁剪（field/title）；空则回落全部 @Excel 列。 */
    @Schema(description = "导出列")
    private List<ExcelExportColumnDto> exportColumns;
}
