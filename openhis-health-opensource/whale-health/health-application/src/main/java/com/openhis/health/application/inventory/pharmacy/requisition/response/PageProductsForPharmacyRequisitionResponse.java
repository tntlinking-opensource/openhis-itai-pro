/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.pharmacy.requisition.response;

import com.openhis.health.application.inventory.pharmacy.requisition.dto.ProductDefinitionItemDto;
import com.openhis.whale.core.application.dto.PagedResult;

/**
 * 药房请领选品分页响应（IF-PHA-QL-11）。
 *
 * <p>继承 {@link PagedResult}，统一分页契约（B.10.11）：
 * {@code pageNo} 为 1-based；{@code content} 为数据列表。</p>
 */
public class PageProductsForPharmacyRequisitionResponse extends PagedResult<ProductDefinitionItemDto> {
}
