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

package com.openhis.health.domain.administration.encounter.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 住院就诊上下文查询模型（替代 {@code InpatientInfoReadModel} 读侧投影行）。
 *
 * <p>字段与 {@code hisrm_inpatient_info} 读模型列及
 * {@link com.openhis.health.application.common.assembler.inpatient.InpatientInfoQueueDisplayAssembler} 消费列对齐。</p>
 */
@Getter
@Setter
public class InpatientEncounterContextQueryModel {

    /** 就诊 ID（住院业务主锚点）。 */
    private Long encounterId;

    /** 住院号/业务号。 */
    private String encounterBusNo;

    /** 住院状态枚举 code。 */
    private String statusEnum;

    /** 住院状态展示文本。 */
    private String statusEnumText;

    /** 患者 ID。 */
    private Long patientId;

    /** 患者姓名。 */
    private String patientName;

    /** 患者性别枚举 code。 */
    private String genderEnum;

    /** 患者出生日期。 */
    private LocalDateTime birthDate;

    /** 年龄文本。 */
    private String ageText;

    /** 当前科室 ID。 */
    private Long organizationId;

    /** 当前科室名称。 */
    private String organizationName;

    /** 当前病区 ID。 */
    private Long wardId;

    /** 当前病区名称。 */
    private String wardName;

    /** 当前床位 ID。 */
    private Long bedId;

    /** 当前床位名称。 */
    private String bedName;

    /** 主治医生 ID。 */
    private Long attendingDoctorId;

    /** 主治医生姓名。 */
    private String attendingDoctorName;

    /** 责任护士 ID。 */
    private Long primaryNurseId;

    /** 责任护士姓名。 */
    private String primaryNurseName;

    /** 入院时间。 */
    private LocalDateTime startTime;

    /** 出院时间。 */
    private LocalDateTime endTime;

    /** 入院方式编码。 */
    private String inWayCode;

    /** 出院方式编码。 */
    private String outWayCode;

    /** 累计费用。 */
    private BigDecimal totalAmount;

    /** 已支付金额。 */
    private BigDecimal paidAmount;

    /** 欠费金额。 */
    private BigDecimal arrearsAmount;

    /** 诊断摘要 JSON。 */
    private String diagnosisJson;

    /** 位置摘要 JSON。 */
    private String locationJson;

    /** 预入院时间。 */
    private LocalDateTime preAdmissionTime;

    /** 预入院科室 ID。 */
    private Long preAdmissionOrgId;

    /** 转科目标科室 ID（转科中时有值）。 */
    private Long transferOrgId;

    /** 出院类型枚举 code。 */
    private String dischargeTypeEnum;
}
