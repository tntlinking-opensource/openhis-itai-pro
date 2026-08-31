/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.consumableusage.outpatient.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.report.consumableusage.support.ConsumableUsageReportAmountEnricher;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 门诊耗材使用报表明细行（IF-RPT-OCU-03；两路并集平铺）。
 */
@Data
@Schema(description = "门诊耗材使用报表明细行")
public class OutpatientConsumableUsageDetailRowDto implements ConsumableUsageReportAmountEnricher.PatientDispenseAmountLine {

    /** 来源码：PATIENT_DISPENSE / DEPT_ISSUE。 */
    @Schema(description = "来源码")
    private String sourceCode;

    /** 来源展示名。 */
    @Schema(description = "来源展示名")
    private String sourceCode_dictText;

    /** 发放主键；科室领用行为空。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "发放主键")
    private Long dispenseId;

    /**
     * 源发放主键（退药 {@code based_on}；正向空）。
     *
     * <p>不落前端展示；金额 enrich 回挂原账单用。</p>
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "源发放主键（退药）", hidden = true)
    private Long basedOnDispenseId;

    /** 耗材请求主键；可空。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "耗材请求主键")
    private Long deviceReqId;

    /** 供应请求行主键；患者发放行为空。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "供应请求行主键")
    private Long supplyRequestId;

    /** 业务单号（科室领用）。 */
    @Schema(description = "业务单号")
    private String busNo;

    /** 耗材定义主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "耗材定义主键")
    private Long deviceDefId;

    /** 耗材名称。 */
    @Schema(description = "耗材名称")
    private String deviceName;

    /** 规格/型号。 */
    @Schema(description = "规格")
    private String specText;

    /** 就诊主键；科室领用可空。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊主键")
    private Long encounterId;

    /** 门诊号。 */
    @Schema(description = "门诊号")
    private String encounterBusNo;

    /** 患者主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "患者主键")
    private Long patientId;

    /** 患者姓名。 */
    @Schema(description = "患者姓名")
    private String patientName;

    /** 科室组织主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "科室组织主键")
    private Long organizationId;

    /** 科室名称。 */
    @Schema(description = "科室名称")
    private String organizationName;

    /** 库位主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "库位主键")
    private Long locationId;

    /** 库位名称。 */
    @Schema(description = "库位名称")
    private String locationName;

    /** 状态码（发放）或类型码（领用/退领）。 */
    @Schema(description = "状态或类型码")
    private String statusOrTypeEnum;

    /** 状态/类型展示。 */
    @Schema(description = "状态或类型展示")
    private String statusOrTypeEnum_enumText;

    /** 数量（退发/退领为负）。 */
    @Schema(description = "数量")
    private BigDecimal quantity;

    /** 单位码。 */
    @Schema(description = "单位码")
    private String unitCode;

    /** 单位展示。 */
    @Schema(description = "单位展示")
    private String unitCode_dictText;

    /** 批号。 */
    @Schema(description = "批号")
    private String lotNumber;

    /** 业务时间。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "业务时间")
    private LocalDateTime businessTime;

    /** 金额（售价别名；退为负；6 位行刻度）。 */
    @Schema(description = "金额")
    private BigDecimal amount;

    /** 进价单价（6 位行刻度）。 */
    @Schema(description = "进价单价")
    private BigDecimal purchaseUnitPrice;

    /** 进价总额（退为负；6 位行刻度）。 */
    @Schema(description = "进价总额")
    private BigDecimal purchaseAmount;

    /** 售价单价（6 位行刻度）。 */
    @Schema(description = "售价单价")
    private BigDecimal saleUnitPrice;

    /** 售价总额（退为负；6 位行刻度）。 */
    @Schema(description = "售价总额")
    private BigDecimal saleAmount;

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

    /**
     * 执行人（发耗材人）主键。
     *
     * <p>患者发放取 {@code DeviceDispense.performer_id}；科室领用取审批人 {@code SupplyRequest.approver_id}。</p>
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "执行人主键")
    private Long performerId;

    /** 执行人（发耗材人）姓名。 */
    @Schema(description = "执行人姓名")
    private String performerName;
}
