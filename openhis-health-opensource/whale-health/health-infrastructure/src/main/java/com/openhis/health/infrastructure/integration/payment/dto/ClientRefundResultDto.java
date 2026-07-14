/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.payment.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 银联退货 Win 回传（退费 IF 顶层 {@code clientRefundResult}；映射 {@link
 * com.openhis.health.infrastructure.integration.payment.ExtensionIn.ThirdPartyRefundConfirmExtensionIn}）。
 */
@Data
@Accessors(chain = true)
public class ClientRefundResultDto {

    /** 原 C 交易 merTradeNo。 */
    private String merTradeNo;
    /** 原 {@code trade_no}。 */
    private String tradeNo;
    /** 逆向 merTradeNo；缺省由 Whale 生成。 */
    private String refundMerTradeNo;
    /** Win refund 响应码；成功 {@code 00}。 */
    private String respCode;
    /** 可读描述。 */
    private String respMsg;
    /** 退货金额（元，scale=2）。 */
    private BigDecimal refundAmountYuan;
}
