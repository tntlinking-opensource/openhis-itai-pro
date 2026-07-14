/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.domain.settle.access;

import com.openhis.wi.domain.settle.command.RefreshWorkInjuryPreSettleAmountsCommand;
import com.openhis.wi.domain.settle.result.RefreshWorkInjuryPreSettleAmountsResult;

/** 2206 后刷新收银草稿应付金额端口。 */
public interface RefreshWorkInjuryPreSettleAmountsAccess {

    RefreshWorkInjuryPreSettleAmountsResult refresh(RefreshWorkInjuryPreSettleAmountsCommand command);
}
