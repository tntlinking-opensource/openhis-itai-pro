package com.openhis.whale.unionpay.bpc.contracts.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** 被扫 C 预下单（prepareConsumerScan）。 */
@Data
@Accessors(chain = true)
public class PrepareConsumerScanCommand {

    private Long paymentId;
    private Integer detailIndex;
    private BigDecimal amount;
    private String fixmedinsCode;
    private Long operatorId;
    private Long orgId;
}
