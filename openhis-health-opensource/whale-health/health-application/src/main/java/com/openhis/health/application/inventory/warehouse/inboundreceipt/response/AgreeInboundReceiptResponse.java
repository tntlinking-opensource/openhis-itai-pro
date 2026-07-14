/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.inboundreceipt.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 审批同意药库入库响应（IF-WHS-IN-08）。
 */

@Getter
@Setter
public class AgreeInboundReceiptResponse {

    /**
     * 同意及后续入库编排是否整体成功；与 {@link com.openhis.health.application.inventory.warehouse.inboundreceipt.mapper.InventoryWarehouseInboundReceiptMapper#toAgreeInboundReceiptResponse(boolean)} 入参一致。
     */
    private Boolean success;
}
