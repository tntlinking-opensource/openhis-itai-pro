/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.pharmacy.prescriptionquery.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 门诊已签发处方药品平铺明细行（IF-OPQ-02）。
 *
 * <p>列表按 {@link #prescriptionNo} 方级展示时仅用方头字段；详情抽屉展示本行用药明细字段。
 * 后端仍按药品申请行平铺返回，不做服务端小计行。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊处方药品明细行")
public class OutpatientPharmacyPrescriptionDrugLineDto {

    /** 药品申请主键。 */
    @Schema(description = "药品申请主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long medicationRequestId;

    /** 存库处方号（正方 CP… / 退方 TCP…）。 */
    @Schema(description = "处方号")
    private String prescriptionNo;

    /** 就诊主键。 */
    @Schema(description = "就诊主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 门诊号。 */
    @Schema(description = "门诊号")
    private String encounterBusNo;

    /** 患者主键。 */
    @Schema(description = "患者主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;

    /** 患者姓名。 */
    @Schema(description = "患者姓名")
    private String patientName;

    /**
     * 患者身份证号（明文；列表展示脱敏由前端负责，便于重名区分）。
     */
    @Schema(description = "身份证号（明文，前端脱敏）")
    private String idCard;

    /** 患者联系电话。 */
    @Schema(description = "患者联系电话")
    private String patientPhone;

    /** 开单医生主键（{@code requester_id}）。 */
    @Schema(description = "开单医生主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderingPractitionerId;

    /** 开单医生姓名。 */
    @Schema(description = "开单医生姓名")
    private String orderingPractitionerName;

    /**
     * 开单科室组织主键（药嘱 {@code org_id}，{@code adm_organization.id}；**BR-MRQ-029**）。
     */
    @Schema(description = "开单科室组织主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderingDepartmentId;

    /** 开单科室名称。 */
    @Schema(description = "开单科室名称")
    private String orderingDepartmentName;

    /** 诊断定义主键。 */
    @Schema(description = "诊断定义主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long conditionDefinitionId;

    /** 诊断名称（同处方号下各药绑定诊断并集展示名；方头口径）。 */
    @Schema(description = "诊断名称（处方诊断并集）")
    private String diagnosisName;

    /** 方级含输液（目录 inject_flag ANY）。 */
    @Schema(description = "方级是否含输液")
    private Boolean infusionPrescriptionFlag;

    /** 开立时间展示（yyyy-MM-dd HH:mm:ss；{@code req_opened_time}，空回落 {@code req_authored_time}）。 */
    @Schema(description = "开立时间（req_opened_time）")
    private String reqOpenedTime;

    /** 签发时间追溯（yyyy-MM-dd HH:mm:ss；{@code req_authored_time}，仅追溯不作列表主轴）。 */
    @Schema(description = "签发时间追溯（req_authored_time）")
    private String reqAuthoredTime;

    /** 收费状态 code。 */
    @Schema(description = "收费状态 code")
    private String chargeStatusEnum;

    /** 收费状态文案。 */
    @Schema(description = "收费状态文案")
    private String chargeStatusText;

    /** 发药状态 code。 */
    @Schema(description = "发药状态 code")
    private String dispenseStatusEnum;

    /** 发药状态文案。 */
    @Schema(description = "发药状态文案")
    private String dispenseStatusText;

    /** 处方类别 code。 */
    @Schema(description = "处方类别 code")
    private String rxTypeCode;

    /** 处方类别文案。 */
    @Schema(description = "处方类别文案")
    private String rxTypeText;

    /** 医保类别 code（{@code YbMedType}；取绑定诊断首位 med_type）。 */
    @Schema(description = "医保类别 code")
    private String medTypeCode;

    /** 医保类别文案。 */
    @Schema(description = "医保类别文案")
    private String medTypeText;

    /** 费用性质主键。 */
    @Schema(description = "费用性质主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long contractId;

    /** 费用性质分类 code。 */
    @Schema(description = "费用性质分类 code")
    private String contractCategoryCode;

    /** 费用性质分类文案。 */
    @Schema(description = "费用性质分类文案")
    private String contractCategoryText;

    /** 药品定义主键。 */
    @Schema(description = "药品定义主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long medicationId;

    /** 药品名称。 */
    @Schema(description = "药品名称")
    private String medicationName;

    /** 规格。 */
    @Schema(description = "规格")
    private String medicationSpec;

    /** 生产厂家展示文案（由 AppService 按目录 manufacturerId 解析供应商名称；非实体 text 列）。 */
    @Schema(description = "生产厂家")
    private String manufacturerText;

    /** 数量。 */
    @Schema(description = "数量")
    private BigDecimal quantity;

    /** 单位 code。 */
    @Schema(description = "单位 code")
    private String unitCode;

    /** 单位文案。 */
    @Schema(description = "单位文案")
    private String unitText;

    /** 本行目录是否注射。 */
    @Schema(description = "本行是否注射目录")
    private Boolean injectFlag;

    /** 行序。 */
    @Schema(description = "行序")
    private Integer sortNumber;

    /** 医嘱分组主键（输液成组等；详情「分组」列）。 */
    @Schema(description = "医嘱分组主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long groupId;

    /** 给药途径/用法 code。 */
    @Schema(description = "用药途径 code")
    private String methodCode;

    /** 给药途径/用法文案。 */
    @Schema(description = "用药途径文案")
    private String methodText;

    /** 用药频次 code（cus_frequency.rate_code）。 */
    @Schema(description = "用药频次 code")
    private String rateCode;

    /** 用药频次文案。 */
    @Schema(description = "用药频次文案")
    private String rateText;

    /** 单次剂量。 */
    @Schema(description = "单次剂量")
    private BigDecimal dose;

    /** 剂量单位 code。 */
    @Schema(description = "剂量单位 code")
    private String doseUnitCode;

    /** 剂量单位文案。 */
    @Schema(description = "剂量单位文案")
    private String doseUnitText;

    /** 用药天数（dispense_per_duration）。 */
    @Schema(description = "用药天数")
    private Integer dispensePerDuration;

    /** 用法说明 code（术语 dosage_instruction）。 */
    @Schema(description = "用法说明 code")
    private String dosageInstructionCode;

    /** 用法说明文案。 */
    @Schema(description = "用法说明文案")
    private String dosageInstructionText;

    /** 是否皮试。 */
    @Schema(description = "是否皮试")
    private Boolean skinTestFlag;

    /** 是否皮试文案（是/否）。 */
    @Schema(description = "是否皮试文案")
    private String skinTestFlagText;

    /** 单价（元；锚定收费明细 unit_price，无费用行时为空）。 */
    @Schema(description = "单价（元）")
    private BigDecimal unitPrice;

    /**
     * 是否计价：存在非草稿收费明细则为 true（PLANNED 及以后进入收费窗口）。
     */
    @Schema(description = "是否计价")
    private Boolean chargeableFlag;

    /** 是否计价文案（是/否）。 */
    @Schema(description = "是否计价文案")
    private String chargeableFlagText;
}
