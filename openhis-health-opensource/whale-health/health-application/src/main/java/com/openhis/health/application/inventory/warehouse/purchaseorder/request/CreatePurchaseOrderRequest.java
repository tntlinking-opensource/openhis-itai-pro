/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.purchaseorder.request;



import com.openhis.health.application.inventory.warehouse.purchaseorder.dto.PurchaseOrderLineDto;

import lombok.Getter;

import lombok.Setter;



import java.util.List;



/**

 *

 * <p>新建订货单（IF-WHS-PO-04）：领域批量取号生成 {@code busNo}，明细全量写入 {@code wkf_supply_request}。

 * 源/目的位置类型（{@code source_type_enum}/{@code purpose_type_enum}）由后端根据 {@code purposeLocationId}

 * 与 {@code adm_location.form_enum} 推导并落库，请求体不传（B.10.8.1）。

 * {@code typeEnum} 固定为 {@link com.openhis.health.domain.share.enums.SupplyType#CABINET_PURCHASE_ORDER}，由后端写入。</p>

 *

 * <p>应用入口：

 * {@link com.openhis.health.application.inventory.warehouse.purchaseorder.service.InventoryWarehousePurchaseOrderAppService#createPurchaseOrder(CreatePurchaseOrderRequest)}。</p>

 */

@Getter

@Setter

public class CreatePurchaseOrderRequest {



    /**

     * 入库目的药库主键（{@code purpose_location_id}）：须在用户管辖药库门禁内；必填。

     */

    private Long purposeLocationId;



    /**

     * 供应类别：{@link com.openhis.health.domain.share.enums.SupplyCategory#getCode()} 持久化码；

     * 可 blank，缺省按 {@link com.openhis.health.domain.share.enums.SupplyCategory#STOCK_SUPPLY}。

     */

    private String categoryEnum;



    /**

     * 供应商主键（{@code supplier_id}）：可选；传入时须为启用供应商。

     */

    private Long supplierId;



    /**

     * 经手人医务人员主键（{@code practitioner_id}）：前端库管员下拉，必填。

     */

    private Long practitionerId;



    /**

     * 明细行全量列表：每行须含 {@link PurchaseOrderLineDto#getOccurrenceTime()}；修订时同结构见 {@link UpdatePurchaseOrderRequest}。

     */

    private List<PurchaseOrderLineDto> lines;

}
