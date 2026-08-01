/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.consumableusage.outpatient.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 门诊耗材使用报表汇总查询请求（IF-RPT-OCU-02）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊耗材使用报表汇总查询请求")
public class SummarizeOutpatientConsumableUsageReportRequest {

    /** 业务日起（yyyy-MM-dd，闭区间日初）；必填。 */
    @Schema(description = "业务日起（yyyy-MM-dd）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String dateFrom;

    /** 业务日止（yyyy-MM-dd，闭区间日末）；必填。 */
    @Schema(description = "业务日止（yyyy-MM-dd）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String dateTo;

    /**
     * 汇总维度：PATIENT / DEPARTMENT；默认 PATIENT。
     * 禁止西药/中药式类型主汇总。
     */
    @Schema(description = "汇总维度 PATIENT/DEPARTMENT")
    private String summaryDimension;

    /**
     * 来源筛：ALL / PATIENT_DISPENSE / DEPT_ISSUE；默认 ALL。
     */
    @Schema(description = "来源筛 ALL/PATIENT_DISPENSE/DEPT_ISSUE")
    private String sourceCode;

    /** 可选库位筛选（患者侧 locationId / 科室侧 purposeLocationId）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "库位主键")
    private Long locationId;

    /** 费用性质合同业务号（{@code fin_contract.bus_no}）；空则不筛选。 */
    @Schema(description = "费用性质合同业务号")
    private String feeNatureContractNo;
}
