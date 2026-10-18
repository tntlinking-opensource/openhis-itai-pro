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
 * IF-REG-12：挂号台加载有效预约请求。
 */
@Data
@Accessors(chain = true)
@Schema(description = "挂号台加载有效预约请求")
public class LoadAppointmentForRegisterRequest {

    /** 患者ID（业务字段）。 */
    @Schema(description = "患者ID")
    private Long patientId;

    /** 预约单号。 */
    @Schema(description = "预约单号")
    private String appointmentBusNo;

    /** 挂号自然日；默认当日。 */
    @Schema(description = "挂号自然日")
    private LocalDate registrationDate;
}
