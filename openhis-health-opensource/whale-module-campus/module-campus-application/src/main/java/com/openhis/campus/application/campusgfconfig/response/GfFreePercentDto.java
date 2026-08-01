/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.application.campusgfconfig.response;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class GfFreePercentDto {

    private Long id;

    @NotBlank
    private String classEnum;

    @NotNull
    private BigDecimal selfPayRatio;

    private String chargeModeEnum;
}
