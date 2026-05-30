/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.inventory.warehouse.inboundreceipt.response;

import com.openhis.health.application.inventory.warehouse.inboundreceipt.dto.ProductDefinitionItemDto;
import com.openhis.whale.core.application.dto.PagedResult;

/**
 *
 * <p>继承 {@link PagedResult}，统一分页契约（B.10.11）：
 * {@code pageNo} 为 1-based；{@code content} 为选品列表数据（产品定义粒度，非按单号聚合）。</p>
 *
 * <p>行内单位/分类等编码类字段为字典或持久化字符串（B.10.8.1）；
 * {@code purchasePrice} 由应用服务在 Mapper 初次装配后按 SDD 写入；
 * {@code availableQuantity} 在进货选品恒为 {@code null}。</p>
 */
public class PageProductsForInboundReceiptResponse extends PagedResult<ProductDefinitionItemDto> {
}
