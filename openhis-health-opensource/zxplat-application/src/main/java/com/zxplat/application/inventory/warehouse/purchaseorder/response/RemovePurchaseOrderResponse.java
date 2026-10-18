/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.purchaseorder.response;

import lombok.Getter;
import lombok.Setter;

/**
 *
 * <p>删除订货单成功响应（IF-WHS-PO-10）。</p>
 */
@Getter
@Setter
public class RemovePurchaseOrderResponse {

    /**
     * 是否删除成功；成功时为 {@code true}。
     */
    private Boolean success;
}
