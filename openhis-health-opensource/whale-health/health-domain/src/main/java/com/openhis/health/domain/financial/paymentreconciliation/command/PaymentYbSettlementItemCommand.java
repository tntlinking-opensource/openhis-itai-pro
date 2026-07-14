/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.financial.paymentreconciliation.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 支付流水落账时写入的医保结算凭证行。
 */
@Getter
@Setter
public class PaymentYbSettlementItemCommand {

    /** 医保结算 ID（setl_id）。 */
    private String setlId;
    /** 收费批次号（chrg_bchno）。 */
    private String chrgBchno;
    /** 就诊 ID（mdtrt_id）。 */
    private String mdtrtId;
    /** 院内医保结算记录主键（可空，对账后回写）。 */
    private Long settlementRecordId;
    /** 医保账户主键（个账/统筹账户维度）。 */
    private Long accountId;
    /** 凭证状态持久化码；默认 {@code CONFIRMED}。 */
    private String status;
}
