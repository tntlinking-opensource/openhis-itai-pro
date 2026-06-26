/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.payment.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** 退货 R / 确认 Win 退货回传结果。 */
@Data
@Accessors(chain = true)
public class ThirdPartyRefundResult {

    /** {@code respCode=00}。 */
    private boolean success;
    /** {@code upc_bpc_order.id}（逆向单）。 */
    private Long thirdPartyOrderId;
    /** 响应描述。 */
    private String respMsg;
}
