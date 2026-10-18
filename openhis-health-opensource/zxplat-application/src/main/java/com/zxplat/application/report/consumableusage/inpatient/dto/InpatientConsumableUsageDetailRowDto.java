/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.consumableusage.inpatient.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.application.report.consumableusage.support.ConsumableUsageReportAmountEnricher;
import com.zxplat.domain.share.constants.GeneralSharedConstants;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 住院耗材使用报表明细行（IF-RPT-ICU-03；两路并集平铺）。
 */
@Data
@Schema(description = "住院耗材使用报表明细行")
public class InpatientConsumableUsageDetailRowDto implements ConsumableUsageReportAmountEnricher.PatientDispenseAmountLine {

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

    /** 住院号。 */
    @Schema(description = "住院号")
    private String encounterBusNo;

    /** 病区名称。 */
    @Schema(description = "病区名称")
    private String wardName;

    /** 床号。 */
    @Schema(description = "床号")
    private String bedName;

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

    /** 实发批号（发放单 {@code lot_number}；明细展示主列）。 */
    @Schema(description = "批号")
    private String lotNumber;

    /**
     * 收费锚点批号（{@code ChargeItem.definitionId} → {@code LOT_NUMBER_PRICE}/{@code LOT_NUMBER_COST}
     * 行的 {@code lot_no}）。
     *
     * <p>仅明细分页质检回填；与 {@link #lotNumber} 比对。不一致时 {@link #lotMismatch}=true。
     * 退耗可经 {@link #basedOnDispenseId} 回挂源发放账批。<strong>不改正额</strong>。</p>
     */
    @Schema(description = "收费锚点批号")
    private String chargeLotNumber;

    /**
     * 账批 ≠ 实发批标记（仅质检展示；金额/汇总口径不变）。
     *
     * <p>{@code true}=两边均有批号且不相等；无账单/无批号/科室领用时保持 null。</p>
     */
    @Schema(description = "账批与实发批是否不一致")
    private Boolean lotMismatch;

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
