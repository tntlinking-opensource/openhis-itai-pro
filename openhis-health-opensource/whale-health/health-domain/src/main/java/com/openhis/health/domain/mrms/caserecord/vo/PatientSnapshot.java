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
 * 从 {@code adm_patient} 组装，映射首页 4401 患者基础字段。</p>
 */
@Getter
@Setter
public class PatientSnapshot {

    /** 患者主键 {@code adm_patient.id}。 */
    private Long patientId;

    /** 患者姓名（4401 psn_name，来自 {@code adm_patient.name}）。 */
    private String name;

    /** 性别字典码（4401 gend，来自 {@code adm_patient.gender_enum}）。 */
    private String genderCode;

    /** 出生日期（4401 brdy，来自 {@code adm_patient.birth_date}）。 */
    private LocalDate birthDate;

    /** 证件号码（4401 certno，来自 {@code adm_patient.id_card}）。 */
    private String certno;

    /** 现住址（4401 curr_addr；Wave1 自动取值暂未回填，预留扩展）。 */
    private String currAddr;

    /** 联系电话（4401 psn_tel，来自 {@code adm_patient.phone}）。 */
    private String phone;

    /** 联系人姓名（4401 coner_name；Wave1 自动取值暂未回填，预留扩展）。 */
    private String contactName;

    /** 联系人电话（4401 coner_tel；Wave1 自动取值暂未回填，预留扩展）。 */
    private String contactPhone;

    /** 国籍字典码（4401 ntly；Wave1 自动取值暂未回填，预留扩展）。 */
    private String nationalityCode;

    /** 民族字典码（4401 naty，GB/T 3304；Wave1 自动取值暂未回填，预留扩展）。 */
    private String natyCode;
}
