/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.consumableusage.inpatient.request;

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
     * 汇总维（仅 SUMMARY Tab）：PATIENT / DEPARTMENT。
     */
    @Schema(description = "汇总维度 PATIENT/DEPARTMENT")
    private String summaryDimension;

    /**
     * 来源筛：ALL / PATIENT_DISPENSE / DEPT_ISSUE。
     */
    @Schema(description = "来源筛 ALL/PATIENT_DISPENSE/DEPT_ISSUE")
    private String sourceCode;

    /** 可选库位筛选。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "库位主键")
    private Long locationId;

    /** 费用性质合同业务号（{@code fin_contract.bus_no}）；空则不筛选。 */
    @Schema(description = "费用性质合同业务号")
    private String feeNatureContractNo;
}
