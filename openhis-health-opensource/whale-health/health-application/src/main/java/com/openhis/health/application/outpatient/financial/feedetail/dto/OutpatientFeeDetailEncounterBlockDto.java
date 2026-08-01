/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.feedetail.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 门诊费用明细就诊块 DTO（IF-OP-FDT-02）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊费用明细就诊块")
public class OutpatientFeeDetailEncounterBlockDto {

    /** 就诊主键。 */
    @Schema(description = "就诊主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 患者姓名。 */
    @Schema(description = "患者姓名")
    private String patientName;

    /** 门诊号 busNo。 */
    @Schema(description = "门诊号")
    private String busNo;

    /** 挂号科室名称。 */
    @Schema(description = "挂号科室")
    private String registrationDepartmentName;

    /** 费用性质合同业务号（优先账户 → fin_contract.bus_no）。 */
    @Schema(description = "费用性质合同业务号")
    private String feeNatureContractNo;

    /** 费用性质展示名（合同名称）。 */
    @Schema(description = "费用性质")
    private String feeNatureDisplay;

    /** 医保医疗类别 code（主诊断优先）。 */
    @Schema(description = "医疗类别 code")
    private String medType;

    /** 医保医疗类别展示文案。 */
    @Schema(description = "医疗类别")
    private String medTypeDisplay;

    /** 就诊日期展示（yyyy-MM-dd）。 */
    @Schema(description = "就诊日期")
    private String encounterDate;

    /** 块内明细行（按 billDate 倒序）。 */
    @Schema(description = "明细行")
    private List<OutpatientFeeDetailLineDto> lines;

    /** 就诊块金额小计。 */
    @Schema(description = "就诊小计")
    private OutpatientFeeDetailAmountSummaryDto subtotal;
}
