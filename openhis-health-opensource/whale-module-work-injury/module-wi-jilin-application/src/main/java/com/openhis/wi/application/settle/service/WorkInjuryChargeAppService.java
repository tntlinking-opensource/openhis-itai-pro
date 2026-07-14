/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.application.settle.service;

import com.openhis.wi.domain.settle.access.RefreshWorkInjuryPreSettleAmountsAccess;
import com.openhis.wi.domain.settle.command.RefreshWorkInjuryPreSettleAmountsCommand;
import com.openhis.wi.domain.settle.result.RefreshWorkInjuryPreSettleAmountsResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 工伤门诊收银（POST /api/app/workInjuryCharge/*）。 */
@Service
@RequiredArgsConstructor
public class WorkInjuryChargeAppService {

    private final RefreshWorkInjuryPreSettleAmountsAccess refreshWorkInjuryPreSettleAmountsAccess;

    public RefreshWorkInjuryPreSettleAmountsResult refreshPreSettleAmounts(
            RefreshWorkInjuryPreSettleAmountsCommand command) {
        return refreshWorkInjuryPreSettleAmountsAccess.refresh(command);
    }
}
