/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.registration.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 首次建档响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "首次建档响应")
public class SavePatientForFirstRegisterResponse {

    /** 患者ID（业务字段）。 */
    @Schema(description = "患者ID")
    private Long patientId;

    /** 患者业务号。 */
    @Schema(description = "患者业务号")
    private String patientBusNo;
}
