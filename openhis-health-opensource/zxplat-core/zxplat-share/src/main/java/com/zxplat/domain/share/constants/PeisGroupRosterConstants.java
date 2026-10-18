/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

/**
 * 团检名册支付/到检状态码。
 */
public final class PeisGroupRosterConstants {

    public static final String PAY_UNPAID = "UNPAID";
    /** 真实收费后建单（AS-PE-FIN-002）。 */
    public static final String PAY_PAID = "PAID";
    /** 免收费建单（P0-1 团检当年不交钱）。 */
    public static final String PAY_ENROLLED = "ENROLLED";
    public static final String ARRIVAL_PENDING = "PENDING";
    public static final String ARRIVAL_ARRIVED = "ARRIVED";

    /** 名册是否已完成建单（含免收费与真实收费）。 */
    public static boolean isEnrolled(String payStatusCode) {
        return PAY_PAID.equals(payStatusCode) || PAY_ENROLLED.equals(payStatusCode);
    }

    private PeisGroupRosterConstants() {
    }
}
