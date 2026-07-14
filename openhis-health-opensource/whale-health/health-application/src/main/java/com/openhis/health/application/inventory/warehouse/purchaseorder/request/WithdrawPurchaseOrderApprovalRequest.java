/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.purchaseorder.request;

import lombok.Getter;
import lombok.Setter;

/**
 *
 * <p>撤回订货单审批（IF-WHS-PO-07）：待审批态整单撤回至草稿/可编辑态。</p>
 *
 * <p>应用入口：
 * {@link com.openhis.health.application.inventory.warehouse.purchaseorder.service.InventoryWarehousePurchaseOrderAppService#withdrawPurchaseOrderApproval(WithdrawPurchaseOrderApprovalRequest)}。</p>
 */
@Getter
@Setter
public class WithdrawPurchaseOrderApprovalRequest {

    /**
     * 订货单业务单号；应用服务门控非空并对首尾空白 {@code trim}。
     */
    private String busNo;
}
