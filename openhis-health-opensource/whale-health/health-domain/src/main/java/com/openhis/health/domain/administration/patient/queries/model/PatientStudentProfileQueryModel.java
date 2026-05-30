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

package com.openhis.health.domain.administration.patient.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 患者学籍合并视图模型。
 */
@Getter
@Setter
public class PatientStudentProfileQueryModel {

    /** 患者标识。 */
    private Long patientId;
    /** 姓名。 */
    private String name;
    /** 身份证号。 */
    private String idCard;
    /** 联系电话。 */
    private String phone;
    /** 学籍记录列表。 */
    private List<PatientStudentQueryModel> studentRecords;
}

