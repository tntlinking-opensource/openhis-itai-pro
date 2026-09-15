/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.feedetail.outpatient.response;

import com.openhis.whale.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 门诊费用明细报表页初始化响应（IF-RPT-OFD-01）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊费用明细报表页初始化响应")
public class LoadOutpatientFeeDetailReportPageInitResponse {

    /** 默认查询日起（yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss；仅日期时起 00:00:00、止 23:59:59，当日）。 */
    @Schema(description = "默认查询日起")
    private String defaultDateFrom;

    /** 默认查询日止（yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss；仅日期时起 00:00:00、止 23:59:59，当日）。 */
    @Schema(description = "默认查询日止")
    private String defaultDateTo;

    /** 默认分页大小（就诊块数/页）。 */
    @Schema(description = "默认分页大小")
    private Integer defaultPageSize;

    /** 收退 Tab 下拉。 */
    @Schema(description = "收退 Tab 选项")
    private List<OptionDto> tradeDirectionOptions;

    /** 费用大类下拉。 */
    @Schema(description = "费用大类选项")
    private List<OptionDto> feeCategoryOptions;

    /** 药品细分类下拉（fin_type_code）。 */
    @Schema(description = "药品细分类选项")
    private List<OptionDto> medicationSubCategoryOptions;

    /** 诊疗细分类下拉（fin_type_code）。 */
    @Schema(description = "诊疗细分类选项")
    private List<OptionDto> activitySubCategoryOptions;

    /** 耗材细分类下拉（fin_type_code）。 */
    @Schema(description = "耗材细分类选项")
    private List<OptionDto> deviceSubCategoryOptions;

    /** 挂号科室下拉。 */
    @Schema(description = "挂号科室选项")
    private List<OptionDto> registrationDepartmentOptions;

    /** 收费员下拉。 */
    @Schema(description = "收费员选项")
    private List<OptionDto> cashierOptions;

    /** 汇总/明细 Tab 选项。 */
    @Schema(description = "呈现模式选项")
    private List<OptionDto> presentationModeOptions;

    /** 默认激活 Tab（SUMMARY）。 */
    @Schema(description = "默认激活 Tab")
    private String defaultActiveTab;

    /** 费用性质下拉（value=fin_contract.bus_no）。 */
    @Schema(description = "费用性质选项")
    private List<OptionDto> feeNatureOptions;

    /** 汇总维度下拉（FIN_TYPE / DEPARTMENT / FEE_CATEGORY / ITEM）。 */
    @Schema(description = "汇总维度选项")
    private List<OptionDto> summaryDimensionOptions;
}
