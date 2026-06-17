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
/** 参与者提交行。 */
@Schema(description = "参与者提交行", requiredProperties = {"roleCode", "practitionerId"})
public class ParticipantSubmitDto {

    /** 参与者角色 code（如 PRIMARY_NURSE、ATTENDING_DOCTOR）。 */
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
