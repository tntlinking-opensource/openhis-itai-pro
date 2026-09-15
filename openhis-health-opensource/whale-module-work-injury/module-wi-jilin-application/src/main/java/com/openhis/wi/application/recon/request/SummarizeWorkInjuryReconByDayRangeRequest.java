/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.application.recon.request;

import lombok.Data;

/** POST /api/app/workInjuryInvoke/summarizeReconByDayRange */
@Data
public class SummarizeWorkInjuryReconByDayRangeRequest {

    private Long contractId;

    /** 与 contractId 二选一。 */
    private String contractBusNo;

    /** yyyy-MM-dd */
    private String begndate;

    /** yyyy-MM-dd */
    private String enddate;
}
