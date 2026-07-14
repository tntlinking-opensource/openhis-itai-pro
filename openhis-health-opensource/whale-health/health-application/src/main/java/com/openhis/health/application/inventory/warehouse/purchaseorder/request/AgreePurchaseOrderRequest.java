/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.purchaseorder.request;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 *
 * <p>订货单同意（IF-WHS-PO-08）：领域 {@code agreeRequest} 仅迁移供应请求审批态为已同意，<strong>不</strong>触库存、
 * <strong>不</strong>生成进货单。业务单号在应用服务侧 {@code trim}；{@link #approverId} 可空时回落为当前操作人。</p>
 *
 * <p>应用入口：
 * {@link com.openhis.health.application.inventory.warehouse.purchaseorder.service.InventoryWarehousePurchaseOrderAppService#agreePurchaseOrder(AgreePurchaseOrderRequest)}。</p>
 */
@Getter
@Setter
public class AgreePurchaseOrderRequest {

    /**
     * 订货业务单号；应用服务门控非空并对首尾空白 {@code trim}。
     */
    private String busNo;

    /**
     * 审批人主键；可空，空时由应用层回落为当前操作人（单岗工作站可不传）。
     */
    private Long approverId;

    /**
     * 审批业务时钟；可空时由领域或命令默认值填充，用于审计与状态迁移时间线。
     */
    private LocalDateTime approvalClock;
}
