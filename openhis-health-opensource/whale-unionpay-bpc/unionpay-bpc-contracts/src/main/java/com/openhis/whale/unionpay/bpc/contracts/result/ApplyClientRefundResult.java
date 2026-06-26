package com.openhis.whale.unionpay.bpc.contracts.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** applyClientRefundResult 结果。 */
@Data
@Accessors(chain = true)
public class ApplyClientRefundResult {

    private boolean success;
    private Long orderId;
    private String merTradeNo;
    private String refundMerTradeNo;
    private String respMsg;
}
