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
 * 就诊诊断读模型：展示就诊与病情的关联及排序、主诊断标记。
 */
@Getter
@Setter
public class EncounterDiagnosisQueryModel {
    /** 就诊诊断关联主键。 */
    private Long diagnosisId;
    /** 就诊主键。 */
    private Long encounterId;
    /** 病情/条件主键。 */
    private Long conditionId;
    /** 是否主诊断。 */
    private Boolean primaryFlag;
    /** 排序序号。 */
    private Integer rank;
    /** 诊断描述。 */
    private String description;
    /** 住院病种类型编码（医保/诊疗类别相关）。 */
    private String diagTypeCode;
    /** 医保医疗类别。 */
    private String medType;
    /** 中医病证组号。 */
    private String tcmSyndromeGroup;
    /** 是否中医诊断关联。 */
    private Boolean tcmFlag;
}
