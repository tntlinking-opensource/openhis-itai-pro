/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.encountermanage.dto;

import com.openhis.health.application.shared.inpatient.AdmissionVitalSignSubmitDto;
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
@Schema(description = "患者全景详情")
public class PatientDetailDto extends PatientQueueItemDto {

    /** 联系电话。 */
    @Schema(description = "联系电话")
    private String patientPhone;

    /** 证件号（展示脱敏）。 */
    @Schema(description = "证件号（展示脱敏）")
    private String idCardNo;

    /** 参与者全角色列表。 */
    @Schema(description = "参与者全角色列表")
    private List<ParticipantDto> participants;

    /** 入院体征回填块。 */
    @Schema(description = "入院体征回填块")
    private AdmissionVitalSignSubmitDto admissionVitalSigns;
}
