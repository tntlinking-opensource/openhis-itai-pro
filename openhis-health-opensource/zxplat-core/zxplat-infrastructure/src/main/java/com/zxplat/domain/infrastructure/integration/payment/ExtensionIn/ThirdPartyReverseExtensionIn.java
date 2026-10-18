/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.payment.ExtensionIn;

import lombok.Data;
import lombok.experimental.Accessors;

/** 撤销 D 出站（P2 · 通道 A · {@code reverse}）。 */
@Data
@Accessors(chain = true)
public class ThirdPartyReverseExtensionIn {

    /** HIS 支付单主键。 */
    private Long paymentId;
    /** 原 C 交易 merTradeNo。 */
    private String merTradeNo;
    /** 操作员。 */
    private Long operatorId;
}
