/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.consumableusage.inpatient.request;

import java.util.List;

import com.openhis.whale.core.application.dto.ExcelExportColumnDto;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 住院耗材使用报表导出请求（IF-RPT-ICU-04）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院耗材使用报表导出请求")
public class ExportInpatientConsumableUsageReportRequest {

    /** 业务日起（yyyy-MM-dd）；必填。 */
    @Schema(description = "业务日起（yyyy-MM-dd）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String dateFrom;

    /** 业务日止（yyyy-MM-dd）；必填。 */
    @Schema(description = "业务日止（yyyy-MM-dd）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String dateTo;

    /**
     * 导出 Tab：SUMMARY / DETAIL；必填。
     */
    @Schema(description = "导出 Tab SUMMARY/DETAIL", requiredMode = Schema.RequiredMode.REQUIRED)
    private String exportTabCode;

    /**
     * 导出自选列（前端按页面列清单下发；空则导出全部 {@code @Excel} 列）。
     */
    @Schema(description = "导出自选列 field+title")
    private List<ExcelExportColumnDto> exportColumns;

    /**
     * 汇总维（仅 SUMMARY Tab）：LOCATION / ITEM / CATEGORY / DEPARTMENT。
     */
    @Schema(description = "汇总维度 LOCATION/ITEM/CATEGORY/DEPARTMENT")
    private String summaryDimension;

    /**
     * 来源筛：PATIENT_DISPENSE / DEPT_ISSUE；空默认患者；ALL 拒绝。
     */
    @Schema(description = "来源筛 PATIENT_DISPENSE/DEPT_ISSUE")
    private String sourceCode;

    /** 可选库位筛选。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "库位主键")
    private Long locationId;

    /** 费用性质合同业务号（{@code fin_contract.bus_no}）；空则不筛选。 */
    @Schema(description = "费用性质合同业务号")
    private String feeNatureContractNo;

    /** 记录类型：ALL / DISPENSE / REFUND；仅患者侧生效。 */
    @Schema(description = "记录类型 ALL/DISPENSE/REFUND")
    private String recordTypeCode;

    /** 耗材类型多选（device_category_code）。 */
    @Schema(description = "耗材类型多选")
    private List<String> deviceCategoryCodes;

    /** 执行人（发耗材人）主键；仅患者侧生效。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "执行人主键")
    private Long performerPractitionerId;

    /** 耗材名称/拼音/编码关键字。 */
    @Schema(description = "耗材关键字")
    private String deviceKeyword;

    /** 患者关键字；仅患者侧生效。 */
    @Schema(description = "患者关键字")
    private String patientKeyword;

    /**
     * 科室组织主键：患者侧=开立/就诊科室；领用侧=领用科室。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "科室组织主键")
    private Long organizationId;
}
