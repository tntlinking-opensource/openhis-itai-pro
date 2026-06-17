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

package com.openhis.health.domain.administration.encounter.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 就诊中医诊断关联读模型（IF-DOR-30/32：按 {@code tcm_syndrome_group} 聚合病证组）。
 */
@Getter
@Setter
public class TcmEncounterDiagnosisAssociationQueryModel {
    /** 就诊诊断关联主键 {@code adm_encounter_diagnosis.id}。 */
    private Long encounterDiagnosisId;
    /** 病情事实主键 {@code cln_condition.id}。 */
    private Long conditionId;
    /** 中医证候组号（同组病名与证候共享）。 */
    private String tcmSyndromeGroup;
    /** 是否主诊病证组。 */
    private Boolean primaryFlag;
    /** 组级排序号。 */
    private Integer rank;
    /** 医保/诊疗医疗类别。 */
    private String medType;
    /** 组级诊断描述。 */
    private String description;
}
