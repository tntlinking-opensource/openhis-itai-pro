/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.domain.settle.result;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 工伤正式结算回执摘要（2207 落账后；2208 退费入口挂接）。
 */
@Data
@Accessors(chain = true)
public class WorkInjurySettlementRecordSummary {

    /** {@code yb_settlement_record.id}。 */
    private Long settlementRecordId;
    /** 本地 {@code fin_payment.id}。 */
    private Long paymentId;
    /** 关联 {@code yb_wi_pre_settle.id}。 */
    private Long preSettleId;
    /** 工伤费用性质 {@code fin_contract.id}。 */
    private Long contractId;
    /** 收费批次号。 */
    private String chrgBchno;
    /** 平台结算 id setl_id。 */
    private String setlId;
    /** 2206 返回单据号 mdtrt_id。 */
    private String mdtrtId;
    /** 本地结算状态（SUCCESS / REVERSED 等）。 */
    private String status;
    /** 2207 Win msgid（审计）。 */
    private String msgid2207;
}
