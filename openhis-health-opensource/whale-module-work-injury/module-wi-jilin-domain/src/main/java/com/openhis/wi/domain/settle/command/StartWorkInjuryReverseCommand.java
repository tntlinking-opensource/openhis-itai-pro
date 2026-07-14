/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.domain.settle.command;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 2208 退费 invoke 会话启动命令（phase=OP_REVERSE）。
 */
@Data
@Accessors(chain = true)
public class StartWorkInjuryReverseCommand {

    /** 原结算 {@code yb_settlement_record.id}（须含 setl_id）。 */
    private Long settlementRecordId;
    /** 原 {@code fin_payment.id}（可空，用于本地挂接）。 */
    private Long paymentId;
    /** 就诊 id。 */
    private Long encounterId;
    /** 工伤费用性质 contract id。 */
    private Long contractId;
    /** 操作员 id。 */
    private Long operatorId;
}
