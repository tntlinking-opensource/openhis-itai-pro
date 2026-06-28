/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.gateway;

import lombok.Data;
import lombok.experimental.Accessors;

/** {@link YbInvokeLogWriter} 入参。 */
@Data
@Accessors(chain = true)
public class YbInvokeLogCommand {

    private Long contractId;
    private String contractBusNo;
    private String infno;
    private String infname;
    private String msgid;
    private Long encounterId;
    private Long paymentDraftId;
    private String chrgBchno;
    private Long preSettleId;
    private Long settlementRecordId;
    private String infcode;
    private String errMsg;
    private Integer durationMs;
    private String requestJson;
    private String responseJson;
    private Long tenantId;
}
