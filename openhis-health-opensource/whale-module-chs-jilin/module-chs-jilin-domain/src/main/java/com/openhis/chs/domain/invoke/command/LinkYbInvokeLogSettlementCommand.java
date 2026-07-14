/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.invoke.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 2207/2304 成功后回填 invoke 日志与结算回执关联 */
@Data
@Accessors(chain = true)
public class LinkYbInvokeLogSettlementCommand {

    /** {@code yb_invoke_log.id} */
    private Long invokeLogId;
    /** {@code yb_settlement_record.id} */
    private Long settlementRecordId;
}
