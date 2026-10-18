/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.purchaseorder.request;

import com.zxplat.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 可关联科室采购申请分页查询（IF-WHS-PO-12）。
 *
 * <p>仅返回已同意（{@link com.zxplat.domain.share.enums.SupplyStatus#AGREE}）且未被药库订货单占用的
 * {@link com.zxplat.domain.share.enums.SupplyType#DEPT_PURCHASE_REQUEST}；按 {@code bus_no} 聚合。</p>
 *
 * <p>应用入口：
 * {@link com.zxplat.application.inventory.warehouse.purchaseorder.service.InventoryWarehousePurchaseOrderAppService#pageLinkableDeptPurchaseRequests(PageLinkableDeptPurchaseRequestsRequest)}。</p>
 */
@Getter
@Setter
public class PageLinkableDeptPurchaseRequestsRequest extends AbstractHealthPageRequest {

    /**
     * 申请科室药房主键；{@code 0} 或空表示不按单一科室收窄（全部可见申请）。
     */
    private Long purposeLocationId;

    /**
     * 申请时间起：可与 {@link #getStartDate()} 二选一，AppService 优先本字段。
     */
    private LocalDateTime applyTimeFrom;

    /**
     * 申请时间止：可与 {@link #getEndDate()} 二选一，AppService 优先本字段。
     */
    private LocalDateTime applyTimeTo;
}
