/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.registration.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;

/**
 * 首次建档请求。
 */
@Data
@Accessors(chain = true)
@Schema(description = "首次建档请求")
public class SavePatientForFirstRegisterRequest {

    /** 姓名（业务字段）。 */
    @Schema(description = "姓名")
    private String name;

    /** 性别编码。 */
    @Schema(description = "性别")
    private String gender;

    /** 身份证号码。 */
    @Schema(description = "身份证号")
    private String idCardNo;

    /** 手机号码。 */
    @Schema(description = "手机号")
    private String phone;

    /** 出生日期。 */
    @Schema(description = "出生日期")
    private LocalDate birthDate;
}
