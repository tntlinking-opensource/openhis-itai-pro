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
 * 患者详情查询模型。
 */
@Getter
@Setter
public class PatientDetailQueryModel {

    /** 患者核心信息。 */
    private PatientCoreQueryModel patient;
    /** 非身份证证件集合。 */
    private List<PatientIdentifierQueryModel> identifiers;
    /** 学籍记录集合。 */
    private List<PatientStudentQueryModel> students;
}

