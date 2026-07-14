/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
@Schema(description = "登记患者主档")
public class RegistrationPatientDto {

    /** 患者 ID（业务字段）。 */
    @Schema(description = "患者 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;

    /** 姓名。 */
    @Schema(description = "姓名")
    private String name;

    /** 身份证号。 */
    @Schema(description = "身份证号")
    private String idCard;

    /** 性别 code。 */
    @Schema(description = "性别 code")
    private String genderEnum;

    /** 出生日期。 */
    @Schema(description = "出生日期")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime birthDate;

    /** 联系电话。 */
    @Schema(description = "联系电话")
    private String phone;
}
