/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.financial.paymentreconciliation;

/** {@link com.zxplat.domain.financial.paymentreconciliation.entity.PaymentYbSettlement} 医保结算状态持久化码。 */
public final class PaymentYbSettlementStatus {

    /** 已确认：医保结算成功且与支付对账一致。 */
    public static final String CONFIRMED = "CONFIRMED";
    /** 已撤销：原结算被冲正或退费撤销。 */
    public static final String REVOKED = "REVOKED";

    private PaymentYbSettlementStatus() {
    }
}
