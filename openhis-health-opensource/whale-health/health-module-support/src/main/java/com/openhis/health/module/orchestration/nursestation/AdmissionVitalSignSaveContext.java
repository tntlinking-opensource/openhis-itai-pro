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

package com.openhis.health.module.orchestration.nursestation;

import com.openhis.health.module.common.dto.nursestation.AdmissionVitalSignSubmitDto;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 入院体征写编排上下文：就诊、时点、床位与提交块。
 */
@Getter
@Setter
@Accessors(chain = true)
public class AdmissionVitalSignSaveContext {

    /** 就诊主键。 */
    private Long encounterId;

    /** 患者主键。 */
    private Long patientId;

    /** 记录时点（入科后须与 {@code adm_encounter.start_time} 一致）。 */
    private LocalDateTime recordedAt;

    /** 记录时病床 {@code Location.id}，未分床时可空。 */
    private Long locationId;

    /** 操作人（审计）。 */
    private Long operatorId;

    /** 记录人 {@code Practitioner.id}，可空。 */
    private Long recorderId;

    /** 前端提交块；空字段不覆盖库内已有值（对齐老系统 merge 语义）。 */
    private AdmissionVitalSignSubmitDto submitDto;
}
