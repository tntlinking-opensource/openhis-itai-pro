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

import java.time.LocalDateTime;

/**
 * 住院信息读模型（{@code hisrm_inpatient_info}）投影用患者人口学快照。
 *
 * <p>来源 {@code adm_patient}，对齐读模型文档 §2 {@code patient_name}/{@code gender_enum}/{@code birth_date}/{@code age_text}。</p>
 */
@Getter
@Setter
public class PatientInpatientProjectionSnapshot {

    /** 患者姓名（{@code adm_patient.name}）。 */
    private String patientName;

    /** 性别枚举 code（{@code adm_patient.gender_enum}）。 */
    private String genderEnum;

    /** 出生日期（日初时刻，与读模型 {@code birth_date} 列一致）。 */
    private LocalDateTime birthDate;

    /** 年龄展示文本（由出生日期派生）。 */
    private String ageText;
}
