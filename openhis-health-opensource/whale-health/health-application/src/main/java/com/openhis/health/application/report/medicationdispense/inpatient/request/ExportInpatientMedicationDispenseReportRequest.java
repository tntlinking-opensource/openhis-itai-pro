/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.medicationdispense.inpatient.request;

import com.openhis.whale.core.application.dto.ExcelExportColumnDto;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 住院发药报表导出请求（IF-RPT-IMD-04）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院发药报表导出请求")
public class ExportInpatientMedicationDispenseReportRequest {

    /** 业务日起（yyyy-MM-dd）。 */
    @Schema(description = "业务日起（yyyy-MM-dd）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String dispenseDateFrom;

    /** 业务日止（yyyy-MM-dd）。 */
    @Schema(description = "业务日止（yyyy-MM-dd）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String dispenseDateTo;

    /** 记录类型。 */
    @Schema(description = "记录类型筛选项 code")
    private String recordTypeCode;

    /** 药房库位主键。 */
    @Schema(description = "药房库位主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long pharmacyLocationId;

    /** 项目类型多选。 */
    @Schema(description = "项目类型 code 多选")
    private List<String> medicationCategoryCodes;

    /** 发药人主键。 */
    @Schema(description = "发药人主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long dispenserPractitionerId;

    /** 药品关键字。 */
    @Schema(description = "药品关键字")
    private String drugKeyword;

    /** 患者关键字。 */
    @Schema(description = "患者关键字")
    private String patientKeyword;

    /** 汇总维度（SUMMARY 模式必填语义）。 */
    @Schema(description = "汇总维度 code")
    private String summaryDimension;

    /** 来源筛选项。 */
    @Schema(description = "来源筛选项 code")
    private String sourceCode;

    /** 呈现模式：SUMMARY / DETAIL（当前 Tab）。 */
    @Schema(description = "导出呈现模式 SUMMARY|DETAIL", requiredMode = Schema.RequiredMode.REQUIRED)
    private String presentationMode;

    /**
     * 导出自选列（前端按页面列清单下发；空则导出全部 {@code @Excel} 列）。
     */
    @Schema(description = "导出自选列 field+title")
    private List<ExcelExportColumnDto> exportColumns;

    /** 费用性质合同业务号（{@code fin_contract.bus_no}）；空则不筛选。 */
    @Schema(description = "费用性质 contract bus_no")
    private String feeNatureContractNo;

    /**
     * 科室组织主键：患者侧=开立科室（药嘱 org_id）；领用侧=领用科室（consuming_organization_id）。
     */
    @Schema(description = "开立/领用科室组织主键（随来源解释）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long organizationId;
}
