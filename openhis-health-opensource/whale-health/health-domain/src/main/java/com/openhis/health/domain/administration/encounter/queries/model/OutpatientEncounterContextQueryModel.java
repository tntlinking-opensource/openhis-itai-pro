/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.encounter.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 门诊就诊上下文查询模型（替代 {@code OutpatientInfoReadModel} 读侧投影行）。
 */
@Getter
@Setter
public class OutpatientEncounterContextQueryModel {

    /** 就诊 ID（业务主锚点）。 */
    private Long encounterId;

    /** 就诊业务号。 */
    private String encounterBusNo;

    /** 就诊类别枚举（门诊）。 */
    private String classEnum;

    /** 就诊状态枚举 code。 */
    private String statusEnum;

    /** 就诊状态展示文本。 */
    private String statusEnumText;

    /** 患者 ID。 */
    private Long patientId;

    /** 患者姓名。 */
    private String patientName;

    /** 患者姓名拼音检索码。 */
    private String pyStr;

    /** 患者姓名五笔检索码。 */
    private String wbStr;

    /** 患者性别枚举 code。 */
    private String genderEnum;

    /** 患者出生日期。 */
    private LocalDateTime birthDate;

    /** 年龄文本。 */
    private String ageText;

    /** 患者身份证号。 */
    private String idCard;

    /** 患者联系电话。 */
    private String phone;

    /** 患者地址全文（主档 {@code Patient.address.text}）。 */
    private String address;

    /** 科室 ID。 */
    private Long organizationId;

    /** 科室名称。 */
    private String organizationName;

    /** 接诊医生 ID。 */
    private Long doctorId;

    /** 接诊医生姓名。 */
    private String doctorName;

    /** 服务类型 ID。 */
    private Long serviceTypeId;

    /** 服务类型名称。 */
    private String serviceTypeName;

    /** 就诊开始时间。 */
    private LocalDateTime startTime;

    /** 接诊时间。 */
    private LocalDateTime receptionTime;

    /** 就诊结束时间。 */
    private LocalDateTime endTime;

    /** 当日序号。 */
    private Integer displayOrder;

    /** 初诊/复诊展示文本。 */
    private String firstEnumText;

    /** 门诊费用总额。 */
    private BigDecimal totalAmount;

    /** 已支付金额。 */
    private BigDecimal paidAmount;

    /** 已退款金额。 */
    private BigDecimal refundAmount;

    /** 诊断快照 JSON。 */
    private String diagnosisJson;

    /** 分诊级别枚举。 */
    private String triageLevelEnum;

    /** 分诊时间。 */
    private LocalDateTime triageTime;
}
