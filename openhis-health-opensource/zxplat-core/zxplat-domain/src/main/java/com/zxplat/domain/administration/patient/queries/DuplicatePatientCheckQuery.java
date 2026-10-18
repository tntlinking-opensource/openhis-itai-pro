/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.patient.queries;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 建档同一人校验查询入参。
 */
@Getter
@Setter
public class DuplicatePatientCheckQuery {

    /**
     * 姓名。
     */
    private String name;

    /**
     * 性别编码。
     */
    private String genderEnum;

    /**
     * 身份证号。
     */
    private String idCard;

    /**
     * 出生日期。
     */
    private LocalDate birthDate;

    /**
     * 年龄。
     */
    private Integer age;
}
