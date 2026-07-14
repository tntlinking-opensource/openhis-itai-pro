/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.outpatient.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 2207 确认命令（从预结算快照发起） */
@Data
@Accessors(chain = true)
public class OpConfirmSettleCommand {

    private Long paymentDraftId;
    private String chrgBchno;
    private Long operatorId;
    private Long tenantId;
}
