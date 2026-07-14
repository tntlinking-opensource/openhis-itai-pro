/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.purchaseorder.response;

import lombok.Getter;
import lombok.Setter;

/**
 *
 * <p>审批驳回成功响应（IF-WHS-PO-09）。</p>
 */
@Getter
@Setter
public class RejectPurchaseOrderResponse {

    /**
     * 是否驳回成功；成功时为 {@code true}。
     */
    private Boolean success;
}
