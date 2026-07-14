/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.inboundreceipt.request;

import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import lombok.Getter;
import lombok.Setter;

/**
 *
 * <p>用于进货新建/编辑时选择「已同意」的药库订货单：须携带 {@link #purposeLocationId}，与订货单
 * {@code purpose_location_id} 一致；应用层按 {@code bus_no} 聚合一行一单（
 * {@code countDistinctBusNo} / {@code listDistinctBusNoPage} / {@code listByBusNosWithFilter}），与可关联请领选单同构。</p>
 *
 * <p>继承 {@link AbstractHealthPageRequest} 的分页与检索键（B.10.11）；返回行为聚合头
 * {@link com.openhis.health.application.inventory.warehouse.inboundreceipt.dto.WarehouseDocumentDetailRowDto}
 *（含 {@code totalAmount}），枚举字段为持久化 {@code getCode()}（B.10.8.1）。</p>
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
