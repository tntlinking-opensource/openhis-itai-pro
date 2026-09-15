/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.application.recon.request;

import lombok.Data;

/** POST /api/app/workInjuryRecon/listSettlementsByDay */
@Data
public class ListWorkInjuryReconSettlementsRequest {

    private Long contractId;

    /** 与 contractId 二选一，对齐 summarizeClearAmounts。 */
    private String contractBusNo;

    /** yyyy-MM-dd */
    private String day;
}
