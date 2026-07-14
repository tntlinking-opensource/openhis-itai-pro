/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.inventorydetail.response;

import com.openhis.health.application.inventory.inventorydetail.dto.InventoryDetailListRowDto;
import com.openhis.whale.core.application.dto.PagedResult;

/**
 * 现库存明细分页响应（IF-INV-DTL-02）。
 *
 * <p>继承 {@link PagedResult}，统一分页契约（B.10.11）：
 * {@code pageNo} 为 1-based；{@code content} 为数据列表。</p>
 */
public class PageInventoryDetailCurrentResponse extends PagedResult<InventoryDetailListRowDto> {
}
