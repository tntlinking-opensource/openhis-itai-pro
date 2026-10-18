/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.prescriptionspotcheck.response;

import com.zxplat.framework.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 门诊处方抽查页初始化响应（IF-RPT-PSC-01）。
 */
@Data
@Schema(description = "门诊处方抽查页初始化响应")
public class LoadPrescriptionSpotCheckReportPageInitResponse {

    /** 默认报表类型（西药及中成药）。 */
    @Schema(description = "默认报表类型")
    private String defaultReportType;

    /** 默认开方时间窗起（当月首日 yyyy-MM-dd）。 */
    @Schema(description = "默认开方时间窗起")
    private String defaultOpenedDateFrom;

    /** 默认开方时间窗止（当月末日 yyyy-MM-dd）。 */
    @Schema(description = "默认开方时间窗止")
    private String defaultOpenedDateTo;

    /** 默认页大小。 */
    @Schema(description = "默认页大小")
    private Integer defaultPageSize;

    /** 报表类型下拉（西药及中成药 / 中药饮片）。 */
    @Schema(description = "报表类型选项")
    private List<OptionDto> reportTypeOptions;
}
