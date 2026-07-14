/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

/**
 * 收银支付渠道（{@code ter_code_system.id=477 pay_enum}）常量。
 *
 * @see com.openhis.health.domain.share.enums.PaymentChannel
 */
public interface PaymentChannelConstants {

    /** {@code ter_code_system.id}，收银能力开关（477 pay_enum）。 */
    long PAY_ENUM_CODE_SYSTEM_ID = 477L;

    /** {@code ter_code_system.name}。 */
    String PAY_ENUM_CODE_SYSTEM_NAME = "pay_enum";
}
