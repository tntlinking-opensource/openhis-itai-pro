/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.mrms.caserecord.vo;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 患者读模型快照（自动取值中间结构，非 QueryModel）。
 *
 * <p>由 {@link com.openhis.health.domain.mrms.caserecord.support.CaseRecordAutoFillSupport#loadPatient}
 * 从 {@code adm_patient} 组装，映射首页患者基础字段。</p>
 */
@Getter
@Setter
public class PatientSnapshot {

    /** 患者主键 {@code adm_patient.id}。 */
    private Long patientId;

    /** 患者姓名（4401 psn_name）。 */
    private String name;

    /**
     * 性别码（首页/4401 gend：{@code 1} 男 {@code 2} 女；由 AdministrativeGender {@code MALE}/{@code FEMALE} 映射）。
     */
    private String genderCode;

    /** 出生日期（4401 brdy）。 */
    private LocalDate birthDate;

    /** 证件号码（4401 certno）。 */
    private String certno;

    /** 现住址全文（4401 curr_addr）。 */
    private String currAddr;

    /** 联系电话（4401 psn_tel）。 */
    private String phone;

    /** 联系人姓名（4401 coner_name）。 */
    private String contactName;

    /** 联系人电话（4401 coner_tel）。 */
    private String contactPhone;

    /** 联系人关系码（4401 coner_rlts）。 */
    private String contactRelationCode;

    /** 国籍字典码（4401 ntly，来自 {@code country_code}）。 */
    private String nationalityCode;

    /** 民族字典码（4401 naty，来自 {@code nationality_code}）。 */
    private String natyCode;

    /** 婚姻状况字典码（4401 mrg_stas）。 */
    private String maritalStatusCode;

    /** 职业字典码（4401 prfs）。 */
    private String prfsCode;

    /** 工作单位及地址（4401 work_org / 展示用）。 */
    private String workCompany;

    /** 籍贯（文本）。 */
    private String nativePlace;
}
