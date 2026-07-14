/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.purchaseorder.response;

import lombok.Getter;
import lombok.Setter;

/**
 *
 * <p>审批同意成功响应（IF-WHS-PO-08）；订货同意不返回库存/进货副作用字段。</p>
 */
@Getter
@Setter
public class AgreePurchaseOrderResponse {

    /**
     * 是否同意成功；成功时为 {@code true}。
     */
    private Boolean success;
}
