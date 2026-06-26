package com.openhis.whale.unionpay.bpc.contracts.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** 确认 Win 退货回传（applyClientRefundResult）。 */
@Data
@Accessors(chain = true)
public class ApplyClientRefundCommand {

    private Long paymentId;
    private String merTradeNo;
    private String tradeNo;
    private String refundMerTradeNo;
    private BigDecimal refundAmount;
    private String respCode;
    private String respMsg;
    private Long operatorId;
}
