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
 * 挂号初始化请求。
 */
@Data
@Accessors(chain = true)
@Schema(description = "挂号初始化请求")
public class LoadRegisterFormInitRequest {

    /** 科室ID（业务字段）。 */
    @Schema(description = "科室ID")
    private Long organizationId;

    /** 医师 ID（号源收窄）。 */
    @Schema(description = "医师ID")
    private Long practitionerId;

    /** 挂号自然日（号源收窄，默认当日）。 */
    @Schema(description = "挂号自然日")
    private LocalDate registrationDate;

    /** 检索关键字。 */
    @Schema(description = "关键字")
    private String keyword;
}
