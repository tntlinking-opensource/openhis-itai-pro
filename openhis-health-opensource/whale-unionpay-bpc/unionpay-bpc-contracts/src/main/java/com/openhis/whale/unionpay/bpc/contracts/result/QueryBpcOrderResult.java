package com.openhis.whale.unionpay.bpc.contracts.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** queryOrder 结果。 */
@Data
@Accessors(chain = true)
public class QueryBpcOrderResult {

    private boolean success;
    private Long orderId;
    private String merTradeNo;
    private String tradeNo;
    private String status;
    private String payType;
    private Long txnAmtFen;
}
