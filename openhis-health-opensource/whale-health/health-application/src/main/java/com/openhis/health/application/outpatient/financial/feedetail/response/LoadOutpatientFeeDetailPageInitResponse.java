/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.outpatient.financial.feedetail.response;

import com.openhis.whale.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 门诊费用明细页初始化响应（IF-OP-FDT-01）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊费用明细页初始化响应")
public class LoadOutpatientFeeDetailPageInitResponse {

    /** 默认查询日起（yyyy-MM-dd，当日）。 */
    @Schema(description = "默认查询日起")
    private String defaultDateFrom;

    /** 默认查询日止（yyyy-MM-dd，当日）。 */
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

    /** 药品细分类下拉（fin_type_code，与日结口径一致）。 */
    @Schema(description = "药品细分类选项（fin_type_code）")
    private List<OptionDto> medicationSubCategoryOptions;

    /** 诊疗细分类下拉（fin_type_code，与日结口径一致）。 */
    @Schema(description = "诊疗细分类选项（fin_type_code）")
    private List<OptionDto> activitySubCategoryOptions;

    /** 耗材细分类下拉（fin_type_code，与日结口径一致）。 */
    @Schema(description = "耗材细分类选项（fin_type_code）")
    private List<OptionDto> deviceSubCategoryOptions;

    /** 挂号科室下拉。 */
    @Schema(description = "挂号科室选项")
    private List<OptionDto> registrationDepartmentOptions;

    /** 收费员下拉。 */
    @Schema(description = "收费员选项")
    private List<OptionDto> cashierOptions;
}
