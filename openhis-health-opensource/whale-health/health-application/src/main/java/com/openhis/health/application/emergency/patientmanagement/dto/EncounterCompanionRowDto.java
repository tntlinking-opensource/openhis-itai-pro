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

package com.openhis.health.application.emergency.patientmanagement.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@Schema(description = "就诊陪护人列表行")
public class EncounterCompanionRowDto {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long companionId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;

    private String name;
    private String idCard;
    private String genderEnum;
    private String phone;
    private String relationshipCode;
    private String idCardSourceEnum;
    private LocalDateTime wristbandPrintedAt;
    private String remark;
    private LocalDateTime createdAt;
}
