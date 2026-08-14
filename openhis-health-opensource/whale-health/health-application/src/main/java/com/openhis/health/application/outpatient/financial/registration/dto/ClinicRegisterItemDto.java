/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.registration.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 门诊挂号列表分页行 DTO。
 *
 * <p>字段命名与读模型 {@code ClinicRegisterReadModel} 语义对齐，由应用层 Mapper 从读模型装配。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊挂号列表项")
public class ClinicRegisterItemDto {

    /** 就诊ID（业务字段）。 */
    @Schema(description = "就诊ID")
    private Long encounterId;

    /** 就诊业务号。 */
    @Schema(description = "就诊业务号")
    private String encounterBusNo;

    /** 患者姓名。 */
    @Schema(description = "患者姓名")
    private String patientName;

    /**
     * 患者性别编码（与读模型 {@code patient_gender_code}、行政性别术语码一致）。
     */
    /** 患者性别编码（业务字段）。 */
    @Schema(description = "患者性别编码")
    private String patientGenderCode;

    /**
     * 患者性别展示文案（与读模型 {@code patient_gender_text} 一致；缺省时由应用层按编码解析）。
     */
    /** 患者性别（展示）（业务字段）。 */
    @Schema(description = "患者性别（展示）")
    private String patientGenderText;

    /**
     * 患者年龄展示文案（由出生日期推算，如 {@code 45岁}、{@code 3岁5月}；无出生日期时为空）。
     */
    /** 患者年龄（展示）（业务字段）。 */
    @Schema(description = "患者年龄（展示）")
    private String patientAgeText;

    /** 患者联系电话（业务字段）。 */
    @Schema(description = "患者联系电话")
    private String contactPhone;

    /** 科室名称（业务字段）。 */
    @Schema(description = "科室名称")
    private String organizationName;

    /** 医生姓名（读模型 doctor_name）。 */
    @Schema(description = "医生姓名")
    private String doctorName;

    /**
     * 就诊状态枚举码（与 {@link com.openhis.health.domain.share.enums.EncounterStatus#getCode()} 持久化值一致，如 {@code "PLANNED"}、{@code "REGISTERED"}、{@code "TRIAGED"}）。
     */
    /** 就诊状态枚举码（业务字段）。 */
    @Schema(description = "就诊状态枚举码")
    private String statusEnum;

    /** 状态文本（业务字段）。 */
    @Schema(description = "状态文本")
    private String statusText;

    /** 挂号时间（业务字段）。 */
    @Schema(description = "挂号时间")
    private LocalDateTime startTime;

    /** 号源类型主键（读模型 service_type_id）。 */
    @Schema(description = "号源类型ID")
    private Long serviceTypeId;

    /** 号源类型名称（读模型 service_type_name）。 */
    @Schema(description = "号源类型名称")
    private String serviceTypeName;

    /** 挂号费金额（来自 {@code adm_charge_item.total_price}，锚点同预结算 service_table+service_id）。 */
    @Schema(description = "挂号费金额")
    private BigDecimal amount;

    /** 打印次数。 */
    @Schema(description = "打印次数")
    private Integer printCount;

    /**
     * 待挂号（PLANNED）时若有预结算草稿，则为支付草稿主键，供调用 {@code payRegisterFee} 完成缴费挂号；已缴费后通常为 null。
     */
    /** 支付草稿ID（待结算挂号时返回）（业务字段）。 */
    @Schema(description = "支付草稿ID（待结算挂号时返回）")
    private Long paymentDraftId;

    /**
     * 已成功挂号/缴费后的挂号费支付流水主键，供退号银联退货与补打挂号小票；待挂号或无支付记录时为 null。
     */
    /** 支付流水ID（挂号费对应 SUCCESS 支付单，供退号/补打小票）（业务字段）。 */
    @Schema(description = "支付流水ID（挂号费对应支付单，供退号/补打小票）")
    private Long paymentId;

    /**
     * 收费人姓名（已成功缴费时由支付流水 {@code enterer_id} 解析医务人员姓名回显；未收费为空）。
     */
    /** 收费人姓名（业务字段）。 */
    @Schema(description = "收费人姓名")
    private String cashierName;

    /**
     * 费用性质名称（挂号身份：就诊优先账户合同名，有医保户优先医保；非账单挂账/支付渠道）。
     */
    @Schema(description = "费用性质名称")
    private String contractName;

    /** 参保地医保区划。 */
    @Schema(description = "参保地医保区划")
    private String insuplcAdmdvs;

    /** 参保地展示名（1901 字典 label）。 */
    @Schema(description = "参保地展示名（1901 字典 label）")
    private String insuplcAdmdvsName;

    /** 是否已医保平台挂号。 */
    @Schema(description = "是否已医保平台挂号")
    private Boolean insurancePlatformRegistered;

    /** 是否已完成医保预结算。 */
    @Schema(description = "是否已完成医保预结算")
    private Boolean insurancePreSettled;

    /** 医保基金支付。 */
    @Schema(description = "医保基金支付")
    private BigDecimal ybFundPaySumamt;

    /** 医保个人账户支付。 */
    @Schema(description = "医保个人账户支付")
    private BigDecimal ybAcctPay;

    /** 医保个人现金支付。 */
    @Schema(description = "医保个人现金支付")
    private BigDecimal ybPsnCashPay;
}
