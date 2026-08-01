/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.purchaseorder.response;

import com.openhis.health.application.inventory.warehouse.purchaseorder.dto.LinkableDeptPurchaseRequestListRowDto;
import com.openhis.health.application.shared.pagination.PagedResult;
import lombok.Getter;
import lombok.Setter;

/**
 * 可关联科室采购申请分页响应（IF-WHS-PO-12；B.10.11）。
 *
 * <p>{@code totalCount} 为按 {@code bus_no} 去重后的单据总数；{@code content} 为当前页聚合行。</p>
 */
@Getter
@Setter
public class PageLinkableDeptPurchaseRequestsResponse extends PagedResult<LinkableDeptPurchaseRequestListRowDto> {
}
