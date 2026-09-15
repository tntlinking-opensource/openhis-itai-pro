/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.purchaseorder.request;

import lombok.Getter;
import lombok.Setter;

/**
 *
 * <p>订货单驳回（IF-WHS-PO-09）：领域 {@code rejectRequest} 迁移审批态为已驳回；须携带驳回原因供审计。</p>
 *
 * <p>应用入口：
 * {@link com.openhis.health.application.inventory.warehouse.purchaseorder.service.InventoryWarehousePurchaseOrderAppService#rejectPurchaseOrder(RejectPurchaseOrderRequest)}。</p>
 */
@Getter
@Setter
public class RejectPurchaseOrderRequest {

    /**
     * 订货业务单号；应用服务门控非空并对首尾空白 {@code trim}。
     */
    private String busNo;

    /**
     * 驳回原因；领域门控非空，供审批轨迹与前端展示。
     */
    private String rejectReason;
}
