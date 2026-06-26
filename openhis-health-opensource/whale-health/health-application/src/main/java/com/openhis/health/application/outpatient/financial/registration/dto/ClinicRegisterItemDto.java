/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
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
/** 门诊挂号列表项。 */
@Schema(description = "门诊挂号列表项")
public class ClinicRegisterItemDto {

    @Schema(description = "就诊ID")
    /** 就诊ID（业务字段）。 */
    private Long encounterId;

    /** 就诊业务号。 */
    @Schema(description = "就诊业务号")
    /** 就诊业务号（业务字段）。 */
    private String encounterBusNo;

    /** 患者姓名。 */
    @Schema(description = "患者姓名")
    /** 患者姓名（业务字段）。 */
    private String patientName;

    /**
     * 患者性别编码（与读模型 {@code patient_gender_code}、行政性别术语码一致）。
     */
    @Schema(description = "患者性别编码")
    /** 患者性别编码（业务字段）。 */
    private String patientGenderCode;

    /**
     * 患者性别展示文案（与读模型 {@code patient_gender_text} 一致；缺省时由应用层按编码解析）。
     */
    @Schema(description = "患者性别（展示）")
    /** 患者性别（展示）（业务字段）。 */
    private String patientGenderText;

    @Schema(description = "患者联系电话")
    /** 患者联系电话（业务字段）。 */
    private String contactPhone;

    @Schema(description = "科室名称")
    /** 科室名称（业务字段）。 */
    private String organizationName;

    /** 医生姓名（读模型 doctor_name）。 */
    @Schema(description = "医生姓名")
    /** 医生姓名（业务字段）。 */
    private String doctorName;

    /**
     * 就诊状态枚举码（与 {@link com.openhis.health.domain.share.enums.EncounterStatus#getCode()} 持久化值一致，如 {@code "PLANNED"}、{@code "REGISTERED"}、{@code "TRIAGED"}）。
     */
    @Schema(description = "就诊状态枚举码")
    /** 就诊状态枚举码（业务字段）。 */
    private String statusEnum;

    @Schema(description = "状态文本")
    /** 状态文本（业务字段）。 */
    private String statusText;

    @Schema(description = "挂号时间")
    /** 挂号时间（业务字段）。 */
    private LocalDateTime startTime;

    /** 号源类型主键（读模型 service_type_id）。 */
    @Schema(description = "号源类型ID")
    /** 号源类型ID（业务字段）。 */
    private Long serviceTypeId;

    /** 号源类型名称（读模型 service_type_name）。 */
    @Schema(description = "号源类型名称")
    /** 号源类型名称（业务字段）。 */
    private String serviceTypeName;

    /** 挂号费金额（来自 {@code adm_charge_item.total_price}，锚点同预结算 service_table+service_id）。 */
    @Schema(description = "挂号费金额")
    /** 挂号费金额（业务字段）。 */
    private BigDecimal amount;

    /** 打印次数。 */
    @Schema(description = "打印次数")
    /** 打印次数（业务字段）。 */
    private Integer printCount;

    /**
     * 待挂号（PLANNED）时若有预结算草稿，则为支付草稿主键，供调用 {@code payRegisterFee} 完成缴费挂号；已缴费后通常为 null。
     */
    @Schema(description = "支付草稿ID（待结算挂号时返回）")
    /** 支付草稿ID（待结算挂号时返回）（业务字段）。 */
    private Long paymentDraftId;

    /**
     * 已成功挂号/缴费后的支付流水主键，供补打挂号小票；待挂号或无支付记录时为 null。
     */
    @Schema(description = "支付流水ID（已结算挂号时返回，供补打小票）")
    /** 支付流水ID（已结算挂号时返回，供补打小票）（业务字段）。 */
    private Long paymentId;

    @Schema(description = "费用性质名称")
    private String contractName;

    @Schema(description = "参保地医保区划")
    private String insuplcAdmdvs;

    @Schema(description = "参保地展示名（1901 字典 label）")
    private String insuplcAdmdvsName;

    @Schema(description = "是否已医保平台挂号")
    private Boolean insurancePlatformRegistered;

    @Schema(description = "是否已完成医保预结算")
    private Boolean insurancePreSettled;

    @Schema(description = "医保基金支付")
    private BigDecimal ybFundPaySumamt;

    @Schema(description = "医保个人账户支付")
    private BigDecimal ybAcctPay;

    @Schema(description = "医保个人现金支付")
    private BigDecimal ybPsnCashPay;
}
