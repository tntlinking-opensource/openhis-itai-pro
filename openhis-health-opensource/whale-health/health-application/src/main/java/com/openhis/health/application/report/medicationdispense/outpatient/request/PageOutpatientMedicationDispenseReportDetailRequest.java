/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.medicationdispense.outpatient.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 门诊发药报表明细分页请求（IF-RPT-OMD-03）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "门诊发药报表明细分页请求")
public class PageOutpatientMedicationDispenseReportDetailRequest extends AbstractHealthPageRequest {

    /** 业务日起（yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss；仅日期时起 00:00:00、止 23:59:59，闭区间）。 */
    @Schema(description = "业务日起（yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss；仅日期时起 00:00:00、止 23:59:59）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String dispenseDateFrom;

    /** 业务日止（yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss；仅日期时起 00:00:00、止 23:59:59，闭区间）。 */
    @Schema(description = "业务日止（yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss；仅日期时起 00:00:00、止 23:59:59）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String dispenseDateTo;

    /** 记录类型：ALL / DISPENSE / REFUND；默认 ALL。 */
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

    /** 来源：PATIENT_DISPENSE / DEPT_ISSUE；空默认患者；ALL 拒绝。 */
    @Schema(description = "来源筛选项 code")
    private String sourceCode;

    /** 费用性质合同业务号（{@code fin_contract.bus_no}）；空则不筛选。 */
    @Schema(description = "费用性质合同业务号")
    private String feeNatureContractNo;
    /**
     * 科室组织主键：患者侧=开立科室（药嘱 org_id）；领用侧=领用科室（consuming_organization_id）。
     */
    @Schema(description = "开立/领用科室组织主键（随来源解释）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long organizationId;
}
