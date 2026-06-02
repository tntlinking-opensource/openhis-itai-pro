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

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 患者管理工作站列表行。
 */
@Data
@Accessors(chain = true)
@Schema(description = "患者管理列表行")
public class PatientManagementListRowDto {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long emgTriageId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long locationId;

    private String patientName;

    private String genderAge;

    private String bedLabel;

    private String areaName;

    private String triageLevelDisplay;

    private String nursingLevelDisplay;

    private String waitDisplay;

    private String flagSummary;

    private boolean focused;

    private String focusNote;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime registrationTime;

    private Long waitMinutes;

    private String encounterBusNo;

    private boolean blacklisted;

    private List<PatientDisplayFlagDto> displayFlags = new ArrayList<>();
}
