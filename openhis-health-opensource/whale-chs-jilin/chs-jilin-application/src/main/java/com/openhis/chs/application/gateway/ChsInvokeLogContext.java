/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.gateway;

import lombok.Data;
import lombok.experimental.Accessors;

/** 网关调用日志业务上下文（ThreadLocal 透传）。 */
@Data
@Accessors(chain = true)
public class ChsInvokeLogContext {

    private Long contractId;
    private Long encounterId;
    private Long paymentDraftId;
    private String chrgBchno;
    private Long preSettleId;
    private Long settlementRecordId;
    private Long tenantId;

    /** 当前 invoke 报文 msgid（由 {@link com.openhis.chs.application.gateway.client.ChsHttpAccessor} 写入）。 */
    private String msgid;
    /** 当前 invoke 交易号。 */
    private String infno;
    /** 最近一次 {@code yb_invoke_log.id}（由持久化装饰器写入）。 */
    private Long lastInvokeLogId;
}
