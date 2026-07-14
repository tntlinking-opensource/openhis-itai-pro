/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.inpatient.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 2304 正式结算命令 */
@Data
@Accessors(chain = true)
public class IpConfirmSettleCommand {

    private Long paymentDraftId;
    private Long preSettleId;
    private Long encounterId;
    private Long operatorId;
    private Long tenantId;
}
