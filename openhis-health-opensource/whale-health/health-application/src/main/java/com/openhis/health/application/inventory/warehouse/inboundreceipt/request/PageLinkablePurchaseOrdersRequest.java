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

import com.openhis.health.application.common.pagination.PageQueryBinder.AbstractHealthPageRequest;
import lombok.Getter;
import lombok.Setter;

/**
 *
 * <p>用于进货新建/编辑时选择「已同意」的药库订货明细：须携带 {@link #purposeLocationId}，与订货单
 * {@code purpose_location_id} 一致，应用层门控后调用
 * {@link com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestQueryService#pageWarehouseDocumentDetail} 分页查询。</p>
 *
 * <p>继承 {@link AbstractHealthPageRequest} 的分页与检索键（B.10.11）；返回行装配为
 * {@link com.openhis.health.application.inventory.warehouse.inboundreceipt.dto.WarehouseDocumentDetailRowDto}，
 * 其中 {@code typeEnum}/{@code statusEnum}/{@code categoryEnum} 等为持久化 {@code getCode()} 字符串（B.10.8.1）。</p>
 *
 * <p>应用入口：
 * {@link com.openhis.health.application.inventory.warehouse.inboundreceipt.service.InventoryWarehouseInboundReceiptAppService#pageLinkablePurchaseOrders(PageLinkablePurchaseOrdersRequest)}。</p>
 */
@Getter
@Setter
public class PageLinkablePurchaseOrdersRequest extends AbstractHealthPageRequest {

    /** 订货目的药库主键；与订货单 {@code purpose_location_id} 一致，用于筛选本药库可关联的订货明细。 */
    private Long purposeLocationId;
}
