package com.openhis.whale.unionpay.bpc.contracts.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 查单 G（queryOrder）。 */
@Data
@Accessors(chain = true)
public class QueryBpcOrderCommand {

    private String merTradeNo;
    private String tradeNo;
    private Long paymentId;
}
