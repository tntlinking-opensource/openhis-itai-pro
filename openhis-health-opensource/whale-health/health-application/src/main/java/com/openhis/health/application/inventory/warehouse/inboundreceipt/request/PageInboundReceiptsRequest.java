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

package com.openhis.health.application.inventory.warehouse.inboundreceipt.request;

import com.openhis.health.module.common.pagination.PageQueryBinder.AbstractHealthPageRequest;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 *
 * <p>继承 {@link AbstractHealthPageRequest}：{@code pageNo}/{@code pageSize}（1-based 页码）、{@code keyword}、
 * {@code startDate}/{@code endDate}、{@code sortBy}/{@code sortDirection}、{@code where} 等（B.10.11）。
 * 列表数据来自写表 {@code wkf_supply_request}：应用层先按过滤条件统计去重 {@code bus_no}，再分页取单号并批量拉回明细行，
 * 由 {@link com.openhis.health.application.inventory.warehouse.inboundreceipt.mapper.InventoryWarehouseInboundReceiptMapper#toInboundReceiptListAggregated}
 * 聚合成 {@link com.openhis.health.application.inventory.warehouse.inboundreceipt.dto.InboundReceiptListRowDto}。</p>
 *
 * <p>应用入口：
 * {@link com.openhis.health.application.inventory.warehouse.inboundreceipt.service.InventoryWarehouseInboundReceiptAppService#pageInboundReceipts(PageInboundReceiptsRequest)}。</p>
 */
@Getter
@Setter
public class PageInboundReceiptsRequest extends AbstractHealthPageRequest {

    /** 当前药库（源位置）主键；与读模型 {@code source_location_id} 过滤一致。 */
    private Long sourceLocationId;

    /**
     * 可选目的库主键；非空时下推读模型 {@code purpose_location_id} 收窄列表，且须与 {@link #sourceLocationId}
     */
    private Long purposeLocationId;
    /**
     * 状态筛选（可选）：{@link com.openhis.health.domain.share.enums.SupplyStatus#getCode()} 持久化字符串，
     * 原样下推读模型查询（B.10.8.1）。
     */
    private String statusEnum;
    /** 申请时间范围下限（含）；与 {@link #applyTimeTo} 成对下推读模型。 */
    private LocalDateTime applyTimeFrom;
    /** 申请时间范围上限（含）。 */
    private LocalDateTime applyTimeTo;
    /**
     * 单据类型筛选（可选）：须为 {@link com.openhis.health.domain.share.enums.SupplyType#CABINET_INBOUND_RECEIPT}
     * 的 {@code getCode()}；非空时由应用层校验，防止借列表窥探其它供应类型。
     */
    private String typeEnum;
}
