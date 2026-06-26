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

package com.openhis.health.application.inpatient.nursestation.encountermanage.dto;

import com.openhis.health.module.common.dto.nursestation.AdmissionVitalSignSubmitDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 患者全景详情（IF-IP-NUR-03）：列表字段超集 + 扩展档。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
/** 患者全景详情。 */
@Schema(description = "患者全景详情")
public class PatientDetailDto extends PatientQueueItemDto {

    /** 联系电话（{@code adm_patient.phone}，展示用）。 */
    @Schema(description = "联系电话")
    private String patientPhone;

    /** 证件号。 */
    @Schema(description = "证件号（展示脱敏）")
    private String idCardNo;

    /** 参与者全角色列表。 */
    @Schema(description = "参与者全角色列表")
    private List<ParticipantDto> participants;

    /** 入科时点入院体征（锚点 {@code adm_encounter.start_time}）。 */
    @Schema(description = "入院体征回填块")
    private AdmissionVitalSignSubmitDto admissionVitalSigns;
}
