/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.registration.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 退号可否校验请求。
 */
@Data
@Accessors(chain = true)
@Schema(description = "退号可否校验请求")
public class ValidateCancelableRequest {

    /** 就诊ID（业务字段）。 */
    @Schema(description = "就诊ID")
    private Long encounterId;

    /** 退号原因。 */
    @Schema(description = "退号原因")
    private String reason;
}
