/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.payment.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 银联退货 Win 回传（退费 IF 顶层 {@code clientRefundResult}；映射 {@link
 * com.zxplat.domain.infrastructure.integration.payment.ExtensionIn.ThirdPartyRefundConfirmExtensionIn}）。
 */
@Data
@Accessors(chain = true)
public class ClientRefundResultDto {

    /** 原 C 交易 merTradeNo。 */
    private String merTradeNo;
    /** 原 {@code trade_no}。 */
    private String tradeNo;
    /** 逆向 merTradeNo；缺省由 ZXPlat 生成。 */
    private String refundMerTradeNo;
    /** Win refund 响应码；成功 {@code 00}。 */
    private String respCode;
    /** 可读描述。 */
    private String respMsg;
    /**
     * Win 退货金额（元，结算刻度 2 位）。
     * 渠道患者实付口径（BPC {@code paid_amt_fen} 优先）；与 HIS 下单冲账金额分账，互不覆盖。
     */
    private BigDecimal refundAmountYuan;
}
