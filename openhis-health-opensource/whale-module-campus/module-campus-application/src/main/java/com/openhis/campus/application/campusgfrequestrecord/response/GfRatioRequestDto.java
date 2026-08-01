/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.application.campusgfrequestrecord.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class GfRatioRequestDto {

    private Long id;
    private String requestNo;
    private String chargeModeEnum;
    private String chargeModeLabel;
    private String itemCode;
    private BigDecimal oldRatio;
    private BigDecimal newRatio;
    private String statusEnum;
    private Long applicantId;
    private String applicantName;
    private LocalDateTime applyTime;
    private LocalDateTime approveTime;
    private String remark;
}
