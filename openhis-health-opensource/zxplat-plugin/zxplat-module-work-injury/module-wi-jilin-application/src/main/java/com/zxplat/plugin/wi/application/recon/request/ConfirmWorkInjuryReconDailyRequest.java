/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.application.recon.request;

import lombok.Data;

/** POST /api/app/workInjuryInvoke/confirmReconDailyBalanced */
@Data
public class ConfirmWorkInjuryReconDailyRequest {

    private Long contractId;

    /** 与 contractId 二选一。 */
    private String contractBusNo;

    /** yyyy-MM-dd */
    private String day;
}
