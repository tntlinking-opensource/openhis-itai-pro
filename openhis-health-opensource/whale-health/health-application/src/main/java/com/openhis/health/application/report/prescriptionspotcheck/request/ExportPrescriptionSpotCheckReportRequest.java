/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.prescriptionspotcheck.request;

import com.openhis.whale.core.application.dto.ExcelExportColumnDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 门诊处方抽查导出请求（IF-RPT-PSC-03）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "门诊处方抽查导出请求")
public class ExportPrescriptionSpotCheckReportRequest
    extends PagePrescriptionSpotCheckReportRequest {

    /** 前端可见列裁剪（field/title）；空则回落全部 @Excel 列。 */
    @Schema(description = "导出列")
    private List<ExcelExportColumnDto> exportColumns;
}
