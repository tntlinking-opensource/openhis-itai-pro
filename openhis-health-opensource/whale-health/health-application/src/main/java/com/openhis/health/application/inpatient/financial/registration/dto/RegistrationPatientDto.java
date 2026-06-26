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

package com.openhis.health.application.inpatient.financial.registration.dto;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 登记弹窗患者主档 DTO。
 */
@Data
@Accessors(chain = true)
/** 登记患者主档。 */
@Schema(description = "登记患者主档")
public class RegistrationPatientDto {

    @Schema(description = "患者 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 患者 ID（业务字段）。 */
    private Long patientId;

    /** 姓名。 */
    @Schema(description = "姓名")
    /** 姓名（业务字段）。 */
    private String name;

    /** 身份证号。 */
    @Schema(description = "身份证号")
    /** 身份证号（业务字段）。 */
    private String idCard;

    /** 性别 code。 */
    @Schema(description = "性别 code")
    /** 性别 code（业务字段）。 */
    private String genderEnum;

    /** 出生日期。 */
    @Schema(description = "出生日期")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    /** 出生日期（业务字段）。 */
    private LocalDateTime birthDate;

    /** 联系电话。 */
    @Schema(description = "联系电话")
    /** 联系电话（业务字段）。 */
    private String phone;
}
