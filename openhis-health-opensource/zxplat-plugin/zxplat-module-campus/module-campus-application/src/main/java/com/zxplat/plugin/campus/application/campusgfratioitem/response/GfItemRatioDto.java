/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.application.campusgfratioitem.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class GfItemRatioDto {

    private Long id;
    private String itemCode;
    private String chargeModeEnum;
    private String chargeModeLabel;
    private BigDecimal ratio;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
}
