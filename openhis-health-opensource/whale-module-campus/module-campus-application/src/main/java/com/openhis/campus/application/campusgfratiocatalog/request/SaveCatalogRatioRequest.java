/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.application.campusgfratiocatalog.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class SaveCatalogRatioRequest {

    private Long tenantId;
    private Long id;

    @NotBlank
    private String catalogCode;

    @NotBlank
    private String chargeModeEnum;

    @NotNull
    private BigDecimal ratio;

    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
}
