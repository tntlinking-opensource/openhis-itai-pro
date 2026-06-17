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
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 就诊参与者展示行（IF-IP-NUR-03）。
 */
@Data
@Accessors(chain = true)
/** 就诊参与者。 */
@Schema(description = "就诊参与者")
public class ParticipantDto {

    @Schema(description = "角色 code")
    private String roleCode;

    @Schema(description = "角色展示名")
    private String roleName;

    /** 医务人员主键。 */
    @Schema(description = "医务人员主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long practitionerId;

    /** 医务人员姓名。 */
    @Schema(description = "医务人员姓名")
    private String practitionerName;
}
