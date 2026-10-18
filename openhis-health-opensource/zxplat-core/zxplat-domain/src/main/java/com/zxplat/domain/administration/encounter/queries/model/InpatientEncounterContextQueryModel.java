/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.encounter.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 住院就诊上下文查询模型（替代 {@code InpatientInfoReadModel} 读侧投影行）。
 *
 * <p>字段与 {@code hisrm_inpatient_info} 读模型列及
 * {@link com.zxplat.domain.application.shared.inpatient.InpatientInfoQueueDisplayAssembler} 消费列对齐。</p>
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

    /** 患者联系电话（主档；信息条回显）。 */
    private String phone;

    /** 患者身份证号（主档；信息条回显）。 */
    private String idCard;

    /** 当前科室 ID。 */
    private Long organizationId;

    /** 当前科室名称。 */
    private String organizationName;

    /** 当前病区 ID（活动病区；无活动时可为末次已完成病区，仅展示）。 */
    private Long wardId;

    /** 当前病区名称（展示口径同 {@link #wardId}）。 */
    private String wardName;

    /** 当前床位 ID（活动床；无活动床时可为末次已完成床，仅展示）。 */
    private Long bedId;

    /**
     * 当前床位名称。
     *
     * <p>无活动床（召回/清床未占床轨）时为「原{房名}-{床名}（未占床）」；无病房名时退化为「原{床名}（未占床）」。</p>
     */
    private String bedName;

    /** 主治医生 ID（活动优先；清床/召回后可为末次已完成参与者，仅展示）。 */
    private Long attendingDoctorId;

    /** 主治医生姓名（展示口径同 {@link #attendingDoctorId}）。 */
    private String attendingDoctorName;

    /** 责任护士 ID（活动优先；无活动行时可为末次已完成，仅展示）。 */
    private Long primaryNurseId;

    /** 责任护士姓名（展示口径同 {@link #primaryNurseId}）。 */
    private String primaryNurseName;

    /** 入院时间。 */
    private LocalDateTime startTime;

    /** 出院时间。 */
    private LocalDateTime endTime;

    /** 入院方式编码。 */
    private String inWayCode;

    /**
     * 入院办理意向（{@code ChargeIntent} code：{@code INSURANCE}/{@code SELF_PAY}）。
     *
     * <p>医生转住院提示款台；可空表示未指定。非真实费用性质。</p>
     */
    private String chargeIntent;

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
