/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.inboundreceipt.response;

import com.openhis.health.application.inventory.warehouse.inboundreceipt.dto.WarehouseDocumentDetailRowDto;
import com.openhis.whale.core.application.dto.PagedResult;

/**
 *
 * <p>继承 {@link PagedResult}，统一分页契约（B.10.11）：
 * {@code pageNo} 为 1-based；{@code content} 为订货相关库房单据明细读模型行。</p>
 *
 * <p><strong>注意</strong>：{@code totalCount} 为读模型<strong>明细行</strong>总数（非按 {@code bus_no} 去重后的单据条数），
 * 与 {@link PageInboundReceiptsResponse} 的 {@code totalCount} 语义不同。</p>
 *
 * <p>行内 {@code typeEnum}/{@code statusEnum}/{@code categoryEnum} 等为持久化 {@code getCode()}（B.10.8.1）。</p>
 */
public class PageLinkablePurchaseOrdersResponse extends PagedResult<WarehouseDocumentDetailRowDto> {
}
