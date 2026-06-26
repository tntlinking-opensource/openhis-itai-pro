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

package com.openhis.health.application.outpatient.nursestation.patient.dto;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 门诊护士站患者队列行 DTO（IF-NUR-01）。
 */
@Data
@Accessors(chain = true)
/** 门诊护士站患者队列行。 */
@Schema(description = "门诊护士站患者队列行")
public class NurseStationPatientQueueItemDto {

    /**
     * 就诊主键。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊主键")
    private Long encounterId;

    /**
     * 患者姓名。
     */
    @Schema(description = "患者姓名")
    private String patientName;

    /**
     * 性别持久化码（AdministrativeGender code）。
     */
    @Schema(description = "性别持久化码")
    private String genderEnum;

    /**
     * 性别展示文案（由 genderEnum 解析）。
     */
    @Schema(description = "性别展示文案")
    private String genderEnumText;

    /**
     * 出生日期。
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_DISPLAY_PATTERN)
    @Schema(description = "出生日期")
    private LocalDate birthDate;

    /**
     * 派生年龄展示。
     */
    @Schema(description = "派生年龄展示")
    private String ageText;

    /**
     * 业务流水/挂号相关展示字段。
     */
    @Schema(description = "业务流水/挂号相关展示字段")
    private String slipNo;

    /**
     * 就诊时间（与门诊信息列表同源口径）。
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "就诊时间")
    private LocalDateTime encounterTime;
}
