/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.medication.medicationdispense.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 门诊药房退药患者队列行（查主表装配，供 AS-OP-PHA-002 列表映射）。
 */
@Getter
@Setter
public class ReturnMedicinePatientQueueRow {

    /** 就诊主键 {@code adm_encounter.id}。 */
    private Long encounterId;
    /** 患者姓名（快照）。 */
    private String patientName;
    /** 性别持久化码。 */
    private String genderEnum;
    /** 性别展示文案。 */
    private String genderEnumText;
    /** 年龄展示串（由出生日期计算）。 */
    private String age;
    /** 证件号原文（列表脱敏前；应用层负责掩码）。 */
    private String idCardRaw;
    /** 就诊科室名称。 */
    private String departmentName;
    /** 退药状态持久化码。 */
    private String refundEnum;
    /** 退药状态展示文案。 */
    private String refundEnumText;
    /** 就诊/挂号业务号。 */
    private String encounterNo;
    /** 接诊/挂号时刻展示串。 */
    private String receptionTime;
}
