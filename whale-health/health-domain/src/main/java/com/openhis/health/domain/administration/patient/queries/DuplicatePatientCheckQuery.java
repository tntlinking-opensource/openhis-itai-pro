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

package com.openhis.health.domain.administration.patient.queries;

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

