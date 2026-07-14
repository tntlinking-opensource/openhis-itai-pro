/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.noop.payment;

import com.openhis.health.infrastructure.integration.payment.ThirdPartyPaymentExtension;

/**
 * 未装配 unionpay-bpc（或任意第三方支付 Module）时的默认 {@link ThirdPartyPaymentExtension}。
 *
 * <p>{@link ThirdPartyPaymentExtension#isEnabled()} 继承接口默认 false；收银台仅展示本地支付方式，
 * 不发起银联/微信等第三方预下单。</p>
 */
public final class NoOpThirdPartyPaymentExtension implements ThirdPartyPaymentExtension {
}
