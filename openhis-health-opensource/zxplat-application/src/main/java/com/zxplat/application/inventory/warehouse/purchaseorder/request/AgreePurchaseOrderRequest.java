/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.purchaseorder.request;

import lombok.Getter;
import lombok.Setter;

/**
 *
 * <p>订货单同意（IF-WHS-PO-08）：领域 {@code agreeRequest} 仅迁移供应请求审批态为已同意，<strong>不</strong>触库存、
 * <strong>不</strong>生成进货单。业务单号在应用服务侧 {@code trim}；审批人由应用层取当前操作人写入领域命令。</p>
 *
 * <p>应用入口：
 * {@link com.zxplat.application.inventory.warehouse.purchaseorder.service.InventoryWarehousePurchaseOrderAppService#agreePurchaseOrder(AgreePurchaseOrderRequest)}。</p>
 */
@Getter
@Setter
public class AgreePurchaseOrderRequest {

    /**
     * 订货业务单号；应用服务门控非空并对首尾空白 {@code trim}。
     */
    private String busNo;
}
