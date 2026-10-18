/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.purchaseorder.request;



import com.zxplat.application.inventory.warehouse.purchaseorder.dto.PurchaseOrderLineDto;

import lombok.Getter;

import lombok.Setter;



import java.util.List;



/**

 *

 * <p>修订订货单（IF-WHS-PO-05）：同号全量明细替换，非增量补丁；位置类型由后端按 {@code purposeLocationId} 推导，请求体不传（B.10.8.1）。

 * {@link #rowVersion} 须与详情 {@link com.zxplat.application.inventory.warehouse.purchaseorder.response.GetPurchaseOrderDetailResponse#getRowVersion()} 一致，

 * 映射领域 {@code expectedRowVersionForRevision}，冲突时 {@code WHS_PO_105}。</p>

 *

 * <p>应用入口：

 * {@link com.zxplat.application.inventory.warehouse.purchaseorder.service.InventoryWarehousePurchaseOrderAppService#updatePurchaseOrder(UpdatePurchaseOrderRequest)}。</p>

 */

@Getter

@Setter

public class UpdatePurchaseOrderRequest {



    /**

     * 既有订货业务单号（{@code bus_no}）：应用服务门控非空；必填。

     */

    private String busNo;



    /**

     * 入库目的药库主键：须在用户管辖药库门禁内；必填。

     */

    private Long purposeLocationId;



    /**

     * 供应类别：{@link com.zxplat.domain.share.enums.SupplyCategory#getCode()}；可 blank，缺省库存供应。

     */

    private String categoryEnum;



    /**

     * 供应商主键：可选；传入时须为启用供应商。

     */

    private Long supplierId;



    /**

     * 经手人医务人员主键：前端库管员下拉，必填。

     */

    private Long practitionerId;



    /**

     * 明细全量替换列表：每行须含 {@link PurchaseOrderLineDto#getOccurrenceTime()}。

     */

    private List<PurchaseOrderLineDto> lines;



    /**

     * 乐观锁版本：须与详情 {@code rowVersion} 及同号 {@code wkf_supply_request.version} 对齐；

     * 映射为领域 {@code expectedRowVersionForRevision}。

     */

    private Long rowVersion;

}
