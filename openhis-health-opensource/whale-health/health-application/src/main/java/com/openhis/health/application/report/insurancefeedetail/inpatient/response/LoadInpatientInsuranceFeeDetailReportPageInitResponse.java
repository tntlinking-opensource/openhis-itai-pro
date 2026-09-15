/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.insurancefeedetail.inpatient.response;

import com.openhis.whale.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 住院医保费用明细页初始化响应（IF-RPT-IIF-01）。
 */
@Data
@Schema(description = "住院医保费用明细页初始化响应")
public class LoadInpatientInsuranceFeeDetailReportPageInitResponse {

    /** 默认结算时间窗起。 */
    @Schema(description = "默认时间窗起")
    private String defaultDateFrom;

    /** 默认结算时间窗止。 */
    @Schema(description = "默认时间窗止")
    private String defaultDateTo;

    /** 默认页大小。 */
    @Schema(description = "默认页大小")
    private Integer defaultPageSize;

    /** 入院科室下拉。 */
    @Schema(description = "科室选项")
    private List<OptionDto> organizationOptions;

    /** 省/市费用类别下拉。 */
    @Schema(description = "费用类别选项")
    private List<OptionDto> contractCategoryOptions;

    /** 是否暴露工伤筛选项。 */
    @Schema(description = "工伤能力位")
    private Boolean workInjuryEnabled;

    /** 职工/居民分组下拉。 */
    @Schema(description = "职工居民选项")
    private List<OptionDto> insutypeGroupOptions;
}
