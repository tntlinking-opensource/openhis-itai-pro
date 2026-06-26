/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.payment.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** 确认 Win 退货回传（通道 C · {@code applyClientRefundResult}）。 */
@Data
@Accessors(chain = true)
public class ThirdPartyRefundConfirmCommand {

    /** HIS 支付单主键。 */
    private Long paymentId;
    /** 原 C 交易 merTradeNo。 */
    private String merTradeNo;
    /** 原 trade_no。 */
    private String tradeNo;
    /** 逆向 merTradeNo。 */
    private String refundMerTradeNo;
    /** 退货金额（元，scale=2）。 */
    private BigDecimal refundAmount;
    /** 响应码。 */
    private String respCode;
    /** 响应描述。 */
    private String respMsg;
    /** 操作员。 */
    private Long operatorId;
}
