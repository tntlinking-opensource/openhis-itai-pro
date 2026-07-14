/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.registration.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 退号可否校验响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "退号可否校验响应")
public class ValidateCancelableResponse {

    /** 是否可退（业务字段）。 */
    @Schema(description = "是否可退")
    private Boolean cancelable;

    /** 不可退号原因码。 */
    @Schema(description = "原因码")
    private String reasonCode;

    /** 不可退号原因说明。 */
    @Schema(description = "原因说明")
    private String reasonText;
}
