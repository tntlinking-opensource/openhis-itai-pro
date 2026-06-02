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

import com.openhis.health.application.inventory.warehouse.inboundreceipt.dto.InboundReceiptListRowDto;
import com.openhis.whale.core.application.dto.PagedResult;

/**
 *
 * <p>继承 {@link PagedResult}，统一分页契约（B.10.11）：
 * {@code pageNo} 为 1-based；{@code content} 为聚合行数据（每 {@code bus_no} 一条）。</p>
 *
 * <p>{@code totalCount} 为按 {@code bus_no} 去重后的<strong>单据条数</strong>（非明细行数）；
 * 展示名字段由应用服务在返回前对 {@code content} 批量富化。</p>
 */
public class PageInboundReceiptsResponse extends PagedResult<InboundReceiptListRowDto> {
}
