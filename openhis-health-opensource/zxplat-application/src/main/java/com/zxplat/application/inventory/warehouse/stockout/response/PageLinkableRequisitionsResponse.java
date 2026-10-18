/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.stockout.response;

import com.zxplat.application.inventory.warehouse.inboundreceipt.dto.WarehouseDocumentDetailRowDto;
import com.zxplat.framework.core.application.dto.PagedResult;

/**
 *
 * <p>继承 {@link PagedResult}，统一分页契约（B.10.11）：
 * {@code pageNo} 为 1-based；{@code content} 为读模型投影行。</p>
 */
public class PageLinkableRequisitionsResponse extends PagedResult<WarehouseDocumentDetailRowDto> {
}
