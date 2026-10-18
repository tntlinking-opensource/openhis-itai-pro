/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.payment.ExtensionOut;

import lombok.Data;
import lombok.experimental.Accessors;

/** 撤销 D 结果（P2）。 */
@Data
@Accessors(chain = true)
public class ThirdPartyReverseExtensionOut {

    /** {@code respCode=00}。 */
    private boolean success;
    /** {@code upc_bpc_order.id}（撤销单）。 */
    private Long thirdPartyOrderId;
    /** 响应描述。 */
    private String respMsg;
}
