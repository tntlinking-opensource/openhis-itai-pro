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

package com.openhis.health.application.emergency.emgtriage.station.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.emergency.emgtriage.station.dto.PatientIdentifierItemDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * IF-EMG-03：急诊建档或补全档案。
 */
@Data
@Accessors(chain = true)
@Schema(description = "急诊分诊-建档请求")
public class RegisterOrUpdatePatientForEmergencyRequest {

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "操作时间")
    private LocalDateTime occurredAt;

    @NotNull
    @Schema(description = "姓名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotNull
    @Schema(description = "性别编码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String genderEnum;

    @NotNull
    @Schema(description = "生日", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate birthDate;

    @Schema(description = "身份证号")
    private String idCard;

    @Schema(description = "联系电话")
    private String phone;

    @Schema(description = "临时建档")
    private Boolean tempFlag;

    @NotNull
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "建档机构", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long organizationId;

    @Schema(description = "非身份证证件集合")
    private List<PatientIdentifierItemDto> identifierItems = new ArrayList<>();
}
