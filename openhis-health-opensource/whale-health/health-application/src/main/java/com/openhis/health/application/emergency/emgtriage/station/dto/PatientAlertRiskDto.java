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

package com.openhis.health.application.emergency.emgtriage.station.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 患者警示风险提示行。
 */
@Data
@Accessors(chain = true)
@Schema(description = "患者警示摘要")
public class PatientAlertRiskDto {

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "患者主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long patientId;

    @Schema(description = "警示级别编码")
    private String alertLevelCode;

    @Schema(description = "提示文案")
    private String message;
}
