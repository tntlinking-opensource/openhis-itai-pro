/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.stockout.response;

import com.openhis.health.application.inventory.warehouse.inboundreceipt.dto.WarehouseDocumentDetailRowDto;
import com.openhis.whale.core.application.dto.PagedResult;

/**
 *
 * <p>继承 {@link PagedResult}，统一分页契约（B.10.11）：
 * {@code pageNo} 为 1-based；{@code content} 为读模型投影行。</p>
 */
public class PageLinkableRequisitionsResponse extends PagedResult<WarehouseDocumentDetailRowDto> {
}
