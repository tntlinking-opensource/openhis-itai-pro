package com.openhis.whale.unionpay.bpc.contracts.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** prepareConsumerScan 结果。 */
@Data
@Accessors(chain = true)
public class PrepareConsumerScanResult {

    private Long orderId;
    private String merTradeNo;
    private String posNo;
    private String mid;
    private String tid;
    private Long txnAmtFen;
    private String transport;
}
