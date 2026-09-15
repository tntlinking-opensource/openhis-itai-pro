/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.consumableusage.outpatient.response;

import java.util.List;

import com.openhis.whale.core.application.dto.OptionDto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 门诊耗材使用报表页初始化响应（IF-RPT-OCU-01）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊耗材使用报表页初始化响应")
public class LoadOutpatientConsumableUsageReportPageInitResponse {

    /** 默认业务日起（yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss；仅日期时起 00:00:00、止 23:59:59）。 */
    @Schema(description = "默认业务日起")
    private String defaultDateFrom;

    /** 默认业务日止（yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss；仅日期时起 00:00:00、止 23:59:59）。 */
    @Schema(description = "默认业务日止")
    private String defaultDateTo;

    /** 汇总维选项。 */
    @Schema(description = "汇总维选项")
    private List<OptionDto> summaryDimensionOptions;

    /** 来源筛选项。 */
    @Schema(description = "来源筛选项")
    private List<OptionDto> sourceCodeOptions;

    /** 科室下拉。 */
    @Schema(description = "科室下拉")
    private List<OptionDto> organizationOptions;

    /** 库房/药房下拉。 */
    @Schema(description = "库房下拉")
    private List<OptionDto> locationOptions;

    /** 费用性质下拉（value=fin_contract.bus_no）。 */
    @Schema(description = "费用性质选项")
    private List<OptionDto> feeNatureOptions;

    /** 耗材类型下拉。 */
    @Schema(description = "耗材类型选项")
    private List<OptionDto> categoryOptions;

    /** 执行人（发耗材人）下拉。 */
    @Schema(description = "执行人选项")
    private List<OptionDto> practitionerOptions;

    /** 记录类型下拉：ALL / DISPENSE / REFUND。 */
    @Schema(description = "记录类型选项")
    private List<OptionDto> recordTypeOptions;
}
