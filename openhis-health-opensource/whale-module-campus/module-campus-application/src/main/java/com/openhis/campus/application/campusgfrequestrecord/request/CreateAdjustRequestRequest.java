/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.application.campusgfrequestrecord.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CreateAdjustRequestRequest {

    private Long tenantId;

    @NotBlank
    private String chargeModeEnum;

    @NotBlank
    private String itemCode;

    @NotNull
    private BigDecimal newRatio;

    private BigDecimal oldRatio;
    private String reason;
}
