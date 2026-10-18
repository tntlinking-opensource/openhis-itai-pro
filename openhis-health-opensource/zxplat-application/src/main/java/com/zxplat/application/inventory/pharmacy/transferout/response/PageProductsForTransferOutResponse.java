/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.pharmacy.transferout.response;

import com.zxplat.application.inventory.pharmacy.transferout.dto.ProductDefinitionItemDto;
import com.zxplat.framework.core.application.dto.PagedResult;

/**
 * 药房调入选品分页响应（IF-PHA-TR-11）。
 *
 * <p>继承 {@link PagedResult}，统一分页契约（B.10.11）：
 * {@code pageNo} 为 1-based；{@code content} 为选品行列表。</p>
 *
 * <p>{@code totalCount} 为库存批号行总条数（未按 {@code bus_no} 去重）。</p>
 */
public class PageProductsForTransferOutResponse extends PagedResult<ProductDefinitionItemDto> {
}
