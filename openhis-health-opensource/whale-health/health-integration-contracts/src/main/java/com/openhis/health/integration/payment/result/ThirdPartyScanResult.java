/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.payment.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** 被扫 C / 确认 Win 回传结果。 */
@Data
@Accessors(chain = true)
public class ThirdPartyScanResult {

    /** {@code respCode=00}。 */
    private boolean success;
    /** {@code upc_bpc_order.id}。 */
    private Long thirdPartyOrderId;
    /** 银联/BPC 交易号。 */
    private String tradeNo;
    /** BPC 原值 pay_type。 */
    private String payType;
    /** 响应描述。 */
    private String respMsg;
    /** 映射后 {@code PaymentChannel} 语义码。 */
    private String channelCode;
}
