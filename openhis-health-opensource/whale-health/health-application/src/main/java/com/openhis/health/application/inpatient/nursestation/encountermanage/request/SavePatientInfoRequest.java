/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.encountermanage.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.shared.inpatient.AdmissionVitalSignSubmitDto;
import com.openhis.health.application.inpatient.nursestation.encountermanage.dto.ParticipantSubmitDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 患者信息维护请求（IF-IP-NUR-04）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "患者信息维护请求", requiredProperties = {"encounterId"})
public class SavePatientInfoRequest {

    /** 住院就诊主键。 */
    @NotNull(message = "就诊标识不能为空")
    @Schema(description = "住院就诊主键", required = true)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 病情/优先级编码。 */
    @Schema(description = "病情/优先级编码")
    private String priorityEnum;

    /** 参与者变更列表。 */
    @Schema(description = "参与者变更列表")
    private List<ParticipantSubmitDto> participants;

    /** 入院体征提交块。 */
    @Schema(description = "入院体征提交块")
    private AdmissionVitalSignSubmitDto admissionVitalSigns;
}
