/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.payment.ExtensionOut;

import lombok.Data;
import lombok.experimental.Accessors;

/** 退货 R / 确认 Win 退货回传结果。 */
@Data
@Accessors(chain = true)
public class ThirdPartyRefundExtensionOut {

    /** {@code respCode=00}。 */
    private boolean success;
    /** {@code upc_bpc_order.id}（逆向单）。 */
    private Long thirdPartyOrderId;
    /** 响应描述。 */
    private String respMsg;
}
