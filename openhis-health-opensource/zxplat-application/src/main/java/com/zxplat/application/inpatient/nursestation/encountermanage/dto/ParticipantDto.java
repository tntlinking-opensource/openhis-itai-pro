/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.encountermanage.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 就诊参与者展示行（IF-IP-NUR-03）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "就诊参与者")
public class ParticipantDto {

    @Schema(description = "角色 code")
    /** 角色 code。 */
    private String roleCode;

    @Schema(description = "角色展示名")
    /** 角色展示名。 */
    private String roleName;

    /** 医务人员主键。 */
    @Schema(description = "医务人员主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long practitionerId;

    /** 医务人员姓名。 */
    @Schema(description = "医务人员姓名")
    private String practitionerName;
}
