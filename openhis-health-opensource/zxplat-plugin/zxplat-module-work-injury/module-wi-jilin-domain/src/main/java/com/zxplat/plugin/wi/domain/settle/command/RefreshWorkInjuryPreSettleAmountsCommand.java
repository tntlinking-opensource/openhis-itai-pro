/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.domain.settle.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 2206 invoke 完成后刷新收银草稿应付金额。 */
@Data
@Accessors(chain = true)
public class RefreshWorkInjuryPreSettleAmountsCommand {

    private Long paymentDraftId;
    private Long encounterId;
}
