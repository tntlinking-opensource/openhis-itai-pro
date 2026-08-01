/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.medicationdispense.outpatient.dto;

import com.openhis.health.application.report.medicationdispense.support.MedicationDispenseReportPricingEnricher;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 门诊发药报表明细行 DTO（患者发退药 ∪ 科室领用/退领）。
 */
@Data
@Schema(description = "门诊发药报表明细行")
public class OutpatientMedicationDispenseDetailRowDto
    implements MedicationDispenseReportPricingEnricher.MedicationDispensePurchaseSaleDetailLine {

    /** 来源 code：PATIENT_DISPENSE / DEPT_ISSUE。 */
    @Schema(description = "来源 code")
    private String sourceCode;

    /** 来源展示。 */
    @Schema(description = "来源展示")
    private String sourceText;

    /** 业务时间（yyyy-MM-dd HH:mm:ss）；排序字段。 */
    @Schema(description = "业务时间")
    private String businessTime;

    /** 发放单主键；科室领用行可为 null。 */
    @Schema(description = "发放单主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long dispenseId;

    /** 供应请求行主键；患者发药行可为 null。 */
    @Schema(description = "供应请求主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long supplyRequestId;

    /** 业务单号（领用单号/汇总单号等）。 */
    @Schema(description = "业务单号")
    private String busNo;

    /** 记录类型 code：DISPENSE / REFUND / DEPT_ISSUE / DEPT_ISSUE_RETURN。 */
    @Schema(description = "记录类型 code")
    private String recordTypeCode;

    /** 记录类型展示。 */
    @Schema(description = "记录类型展示")
    private String recordTypeText;

    /** 药房/科室药房主键。 */
    @Schema(description = "药房主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long pharmacyLocationId;

    /** 药房名称。 */
    @Schema(description = "药房名称")
    private String pharmacyLocationName;

    /** 发药人主键。 */
    @Schema(description = "发药人主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long dispenserPractitionerId;

    /** 发药人姓名。 */
    @Schema(description = "发药人姓名")
    private String dispenserName;

    /** 患者主键。 */
    @Schema(description = "患者主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;

    /** 患者姓名。 */
    @Schema(description = "患者姓名")
    private String patientName;

    /** 就诊主键。 */
    @Schema(description = "就诊主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 门诊号。 */
    @Schema(description = "门诊号")
    private String encounterBusNo;

    /** 处方号。 */
    @Schema(description = "处方号")
    private String prescriptionNo;

    /** 药品定义主键。 */
    @Schema(description = "药品主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long medicationId;

    /** 药品编码。 */
    @Schema(description = "药品编码")
    private String medicationCode;

    /** 药品名称。 */
    @Schema(description = "药品名称")
    private String medicationName;

    /** 规格。 */
    @Schema(description = "规格")
    private String medicationSpec;

    /** 数量（退侧为负）。 */
    @Schema(description = "数量")
    private BigDecimal quantity;

    /** 单位 code。 */
    @Schema(description = "单位 code")
    private String unitCode;

    /** 单位展示。 */
    @Schema(description = "单位展示")
    private String unitText;

    /** 单价（售价别名；兼容旧字段）。 */
    @Schema(description = "单价")
    private BigDecimal unitPrice;

    /** 金额（售价别名；退侧为负）。 */
    @Schema(description = "金额")
    private BigDecimal totalAmount;

    /** 进价单价（6 位行刻度）。 */
    @Schema(description = "进价单价")
    private BigDecimal purchaseUnitPrice;

    /** 进价总额（退侧为负；6 位行刻度）。 */
    @Schema(description = "进价总额")
    private BigDecimal purchaseAmount;

    /** 售价单价（6 位行刻度）。 */
    @Schema(description = "售价单价")
    private BigDecimal saleUnitPrice;

    /** 售价总额（退侧为负；6 位行刻度）。 */
    @Schema(description = "售价总额")
    private BigDecimal saleAmount;

    /** 批号。 */
    @Schema(description = "批号")
    private String lotNumber;

    /** 追溯码。 */
    @Schema(description = "追溯码")
    private String traceNo;

    /** 开单/领用科室主键。 */
    @Schema(description = "科室主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long departmentId;

    /** 开单/领用科室名称。 */
    @Schema(description = "科室名称")
    private String departmentName;

    /** 用法用量。 */
    @Schema(description = "用法用量")
    private String usageText;

    /** 厂家。 */
    @Schema(description = "厂家")
    private String manufacturerText;

    /** 药嘱主键。 */
    @Schema(description = "药嘱主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long medReqId;

    /** 费用性质合同业务号。 */
    @Schema(description = "费用性质合同业务号")
    private String feeNatureContractNo;

    /** 费用性质展示名。 */
    @Schema(description = "费用性质")
    private String feeNatureDisplay;

    /** 医保医疗类别 code。 */
    @Schema(description = "医疗类别 code")
    private String medType;

    /** 医保医疗类别展示文案。 */
    @Schema(description = "医疗类别")
    private String medTypeDisplay;
}
