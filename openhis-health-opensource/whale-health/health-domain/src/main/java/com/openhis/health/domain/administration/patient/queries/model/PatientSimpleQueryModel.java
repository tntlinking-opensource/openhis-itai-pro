/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.patient.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 患者简要信息模型。
 */
@Getter
@Setter
public class PatientSimpleQueryModel {

    /** 患者标识。 */
    private Long id;
    /** 院内编码。 */
    private String busNo;
    /** 姓名。 */
    private String name;
    /** 性别编码。 */
    private String genderEnum;
    /** 出生日期。 */
    private LocalDate birthDate;
    /** 身份证号。 */
    private String idCard;
}
