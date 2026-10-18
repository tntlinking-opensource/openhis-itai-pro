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

 *

 * <p>订货单分页列表（IF-WHS-PO-02）：继承 {@link AbstractHealthPageRequest}（B.10.11）；

 * 列表按 {@code busNo} 聚合返回 {@link com.zxplat.application.inventory.warehouse.purchaseorder.dto.PurchaseOrderListRowDto}。</p>

 *

 * <p>应用入口：

 * {@link com.zxplat.application.inventory.warehouse.purchaseorder.service.InventoryWarehousePurchaseOrderAppService#pagePurchaseOrders(PagePurchaseOrdersRequest)}。</p>

 */

@Getter

@Setter

public class PagePurchaseOrdersRequest extends AbstractHealthPageRequest {



    /**

     * 列表筛选：订货目的药库（必填），对应 {@code wkf_supply_request.purpose_location_id}；

     * {@code 0} 表示当前用户可见全部药库（与 init {@code warehouseLocationOptions} 首项 {@code value="0"} 对齐）。

     */

    private Long purposeLocationId;



    /**

     * 状态筛选：{@link com.zxplat.domain.share.enums.SupplyStatus#getCode()} 持久化字符串；可空表示不限状态。

     */

    private String statusEnum;



    /**

     * 申请时间起：可与 {@link #getStartDate()} 二选一，AppService 优先本字段。

     */

    private LocalDateTime applyTimeFrom;



    /**

     * 申请时间止：可与 {@link #getEndDate()} 二选一，AppService 优先本字段。

     */

    private LocalDateTime applyTimeTo;

}
