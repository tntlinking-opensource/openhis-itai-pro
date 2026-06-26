/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.mrms.contracts.snapshot;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 患者读模型快照（自动取值用）。
 */
@Getter
@Setter
public class PatientSnapshot {

    /** 患者主键。 */
    private Long patientId;

    /** 姓名。 */
    private String name;

    /** 性别字典码。 */
    private String genderCode;

    /** 出生日期。 */
    private LocalDate birthDate;

    /** 证件号。 */
    private String certno;

    /** 现住址。 */
    private String currAddr;

    /** 联系电话。 */
    private String phone;

    /** 联系人姓名。 */
    private String contactName;

    /** 联系人电话。 */
    private String contactPhone;

    /** 国籍字典码。 */
    private String nationalityCode;

    /** 民族字典码。 */
    private String natyCode;
}
