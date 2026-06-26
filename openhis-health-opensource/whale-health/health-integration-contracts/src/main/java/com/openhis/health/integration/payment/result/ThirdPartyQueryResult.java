/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.payment.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** 查单 G 结果。 */
@Data
@Accessors(chain = true)
public class ThirdPartyQueryResult {

    /** 查询成功（非交易成功）。 */
    private boolean success;
    /** 订单状态（如 SUCCESS / FAILED / PENDING）。 */
    private String status;
    /** 银联/BPC 交易号。 */
    private String tradeNo;
    /** BPC 原值 pay_type。 */
    private String payType;
}
