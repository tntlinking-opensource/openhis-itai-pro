/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.unionpay.bpc.domain.refund.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 确认 Win 退货回传（{@code applyClientRefundResult}）。
 *
 * <p>{@code refundAmount} 为渠道患者实付元（有 {@code paid_amt_fen} 用实付/100），
 * 不是医院 HIS 下单冲账金额。</p>
 */
@Data
@Accessors(chain = true)
public class ApplyClientRefundCommand {

    /** HIS 支付单主键；可空（按 merTradeNo 定位原单）。 */
    private Long paymentId;
    /** 原消费商户订单号。 */
    private String merTradeNo;
    /** 原银联交易号。 */
    private String tradeNo;
    /** 逆向商户订单号；空则服务端生成。 */
    private String refundMerTradeNo;
    /**
     * Win 退货金额（元，结算刻度 2 位）。
     * 须等于原单渠道实付分/100（有 paid_amt_fen 用实付，否则 txn_amt_fen）。
     */
    private BigDecimal refundAmount;
    /** Win 响应码；成功为 {@code 00}。 */
    private String respCode;
    /** Win 可读描述。 */
    private String respMsg;
    /** 操作员主键。 */
    private Long operatorId;
}
