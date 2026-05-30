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
@Schema(description = "门诊挂号列表项")
public class ClinicRegisterItemDto {

    /** 就诊 ID。 */
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
    @Schema(description = "患者性别编码")
    private String patientGenderCode;

    /**
     * 患者性别展示文案（与读模型 {@code patient_gender_text} 一致；缺省时由应用层按编码解析）。
     */
    @Schema(description = "患者性别（展示）")
    private String patientGenderText;

    /** 患者联系电话（读模型 contact_phone）。 */
    @Schema(description = "患者联系电话")
    private String contactPhone;

    /** 科室名称。 */
    @Schema(description = "科室名称")
    private String organizationName;

    /** 医生姓名（读模型 doctor_name）。 */
    @Schema(description = "医生姓名")
    private String doctorName;

    /**
     * 就诊状态枚举码（与 {@link com.openhis.health.domain.share.enums.EncounterStatus#getCode()} 持久化值一致，如 {@code "PLANNED"}、{@code "REGISTERED"}、{@code "TRIAGED"}）。
     */
    @Schema(description = "就诊状态枚举码")
    private String statusEnum;

    /** 状态文本。 */
    @Schema(description = "状态文本")
    private String statusText;

    /** 挂号开始时间（读模型 start_time）。 */
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
    @Schema(description = "支付草稿ID（待结算挂号时返回）")
    private Long paymentDraftId;
}
