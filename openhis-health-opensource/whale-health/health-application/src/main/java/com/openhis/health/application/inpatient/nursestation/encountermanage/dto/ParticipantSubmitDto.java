/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.encountermanage.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 护士站患者信息维护 — 参与者提交行。
 */
@Data
@Accessors(chain = true)
@Schema(description = "参与者提交行", requiredProperties = {"roleCode", "practitionerId"})
public class ParticipantSubmitDto {

    /** 参与者角色编码。 */
    @NotBlank(message = "参与者角色不能为空")
    @Schema(description = "参与者角色编码", required = true)
    private String roleCode;

    /** 医务人员主键。 */
    @NotNull(message = "医务人员标识不能为空")
    /** 医务人员主键。 */
    @Schema(description = "医务人员主键", required = true)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long practitionerId;
}
