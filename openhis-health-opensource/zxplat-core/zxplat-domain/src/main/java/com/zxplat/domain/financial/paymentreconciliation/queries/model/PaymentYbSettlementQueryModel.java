/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.financial.paymentreconciliation.queries.model;

import lombok.Getter;
import lombok.Setter;

/** 支付流水关联医保结算凭证读模型（对应 {@code fin_payment_yb_settlement}）。 */
@Getter
@Setter
public class PaymentYbSettlementQueryModel {

    /** 关联行主键。 */
    private Long id;
    /** 支付流水主键。 */
    private Long reconciliationId;
    /** 医保平台 setl_id。 */
    private String setlId;
    /** 收费批次号。 */
    private String chrgBchno;
    /** 医保就诊 ID。 */
    private String mdtrtId;
    /** CHS 侧 {@code yb_settlement_record.id}。 */
    private Long settlementRecordId;
    /** 就诊账户主键。 */
    private Long accountId;
    /** 关联状态：CONFIRMED / REVOKED。 */
    private String status;
}
