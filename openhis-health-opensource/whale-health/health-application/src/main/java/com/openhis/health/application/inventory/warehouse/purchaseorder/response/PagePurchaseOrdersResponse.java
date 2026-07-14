/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.purchaseorder.response;

import com.openhis.health.application.inventory.warehouse.purchaseorder.dto.PurchaseOrderListRowDto;
import com.openhis.health.application.shared.pagination.PagedResult;
import lombok.Getter;
import lombok.Setter;

/**
 *
 * <p>订货单列表分页响应（IF-WHS-PO-02；按 {@code busNo} 聚合行，B.10.11）。
 * 继承 {@link PagedResult}：{@code pageNo} 为 1-based；{@code content} 为 {@link PurchaseOrderListRowDto} 列表。</p>
 */
@Getter
@Setter
public class PagePurchaseOrdersResponse extends PagedResult<PurchaseOrderListRowDto> {
}
