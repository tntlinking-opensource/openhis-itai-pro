/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

/**
 * 团检合同支付状态码。
 */
public final class PeisGroupContractConstants {

    /** 合同未收款。 */
    public static final String PAY_UNPAID = "UNPAID";
    /** 合同已收款。 */
    public static final String PAY_PAID = "PAID";

    public static boolean isPaid(String payStatusCode) {
        return PAY_PAID.equals(payStatusCode);
    }

    private PeisGroupContractConstants() {
    }
}
