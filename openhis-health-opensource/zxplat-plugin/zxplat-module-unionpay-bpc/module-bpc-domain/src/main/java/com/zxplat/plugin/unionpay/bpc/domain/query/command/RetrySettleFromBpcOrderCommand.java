/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.unionpay.bpc.domain.query.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 半成功补结算校验（BPC 已成功、禁止重复 C）。 */
@Data
@Accessors(chain = true)
public class RetrySettleFromBpcOrderCommand {

    /** HIS 支付单（半成功 DRAFT）。 */
    private Long paymentId;

    /** 首次被扫回传的商户订单号。 */
    private String merTradeNo;
}
