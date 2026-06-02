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
