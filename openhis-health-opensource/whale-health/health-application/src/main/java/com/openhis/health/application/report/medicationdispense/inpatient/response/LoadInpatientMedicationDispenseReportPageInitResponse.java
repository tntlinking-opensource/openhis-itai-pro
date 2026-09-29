/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.medicationdispense.inpatient.response;

import com.openhis.whale.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 住院发药报表页初始化响应（IF-RPT-IMD-01）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院发药报表页初始化响应")
public class LoadInpatientMedicationDispenseReportPageInitResponse {

    /** 默认业务日起（yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss；仅日期时起 00:00:00、止 23:59:59）。 */
    @Schema(description = "默认业务日起")
    private String defaultDateFrom;

    /** 默认业务日止（yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss；仅日期时起 00:00:00、止 23:59:59）。 */
    @Schema(description = "默认业务日止")
    private String defaultDateTo;

    /** 启用药房下拉。 */
    @Schema(description = "药房下拉")
    private List<OptionDto> pharmacyOptions;

    /** 药品类型下拉。 */
    @Schema(description = "药品类型下拉")
    private List<OptionDto> categoryOptions;

    /** 发药人下拉。 */
    @Schema(description = "发药人下拉")
    private List<OptionDto> practitionerOptions;

    /** 记录类型下拉。 */
    @Schema(description = "记录类型下拉")
    private List<OptionDto> recordTypeOptions;

    /** 汇总维度下拉。 */
    @Schema(description = "汇总维度下拉")
    private List<OptionDto> summaryDimensionOptions;

    /** 来源下拉。 */
    @Schema(description = "来源下拉")
    private List<OptionDto> sourceOptions;

    /** 费用性质下拉（value=fin_contract.bus_no）。 */
    @Schema(description = "费用性质选项")
    private List<OptionDto> feeNatureOptions;

    /** 开立/领用科室下拉（随来源解释）。 */
    @Schema(description = "开立/领用科室选项")
    private List<OptionDto> organizationOptions;

    /**
     * 药品性质下拉（仅麻醉 / 一类精神 / 二类精神；value=KeyDrugType code）。
     */
    @Schema(description = "药品性质下拉（麻醉/精一/精二）")
    private List<OptionDto> medicinePropertyOptions;
}
