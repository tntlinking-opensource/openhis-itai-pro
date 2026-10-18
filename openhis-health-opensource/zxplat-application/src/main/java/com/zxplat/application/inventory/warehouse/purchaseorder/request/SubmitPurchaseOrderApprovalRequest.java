/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.purchaseorder.request;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 *
 * <p>提交订货单审批（IF-WHS-PO-06）：草稿/撤回态整单提交，写入申请时间 {@code apply_time}。</p>
 *
 * <p>应用入口：
 * {@link com.zxplat.application.inventory.warehouse.purchaseorder.service.InventoryWarehousePurchaseOrderAppService#submitPurchaseOrderApproval(SubmitPurchaseOrderApprovalRequest)}。</p>
 */
@Getter
@Setter
public class SubmitPurchaseOrderApprovalRequest {

    /**
     * 订货单业务单号；应用服务门控非空。
     */
    private String busNo;

    /**
     * 提交审批时的申请时间；可空时由服务端取当前时刻写入 {@code apply_time}。
     */
    private LocalDateTime applyClock;
}
