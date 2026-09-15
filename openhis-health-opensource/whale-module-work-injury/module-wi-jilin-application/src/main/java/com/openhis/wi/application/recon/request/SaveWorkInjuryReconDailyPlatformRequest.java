/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.application.recon.request;

import lombok.Data;

import java.math.BigDecimal;

/** POST /api/app/workInjuryInvoke/saveReconDailyPlatform */
@Data
public class SaveWorkInjuryReconDailyPlatformRequest {

    private Long contractId;

    /** 与 contractId 二选一。 */
    private String contractBusNo;

    /** yyyy-MM-dd */
    private String day;

    private BigDecimal platformMedfee;
    private BigDecimal platformHifp;
    private BigDecimal platformCash;

    /** LOCAL / SNYD / QGYD */
    private String reconScope;
}
