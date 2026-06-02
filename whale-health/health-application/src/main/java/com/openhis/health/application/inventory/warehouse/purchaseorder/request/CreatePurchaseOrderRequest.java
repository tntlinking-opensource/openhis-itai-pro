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

package com.openhis.health.application.inventory.warehouse.purchaseorder.request;

import com.openhis.health.application.inventory.warehouse.purchaseorder.dto.PurchaseOrderLineDto;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 新建订货单请求。
 *
 * <p>源/目的位置类型（{@code source_type_enum}/{@code purpose_type_enum}）由后端根据 {@code purposeLocationId} 等位置主键与
 * {@code adm_location.form_enum} 推导并落库，请求体不传（B.10.8.1）。</p>
 */
@Getter
@Setter
public class CreatePurchaseOrderRequest {

    /** 入库目的药库（必填）。 */
    private Long purposeLocationId;
    /**
     * 供应类别：{@link com.openhis.health.domain.share.enums.SupplyCategory#getCode()}；可 blank，缺省按库存供应
     * （{@link com.openhis.health.domain.share.enums.SupplyCategory#STOCK_SUPPLY}）。
     */
    private String categoryEnum;
    /** 供应商。 */
    private Long supplierId;
    /** 经手人医务人员主键（前端下拉选择）。 */
    private Long practitionerId;
    /** 明细行。 */
    private List<PurchaseOrderLineDto> lines;
    /** 单据类型：固定 1（历史占位，与前端 WHS_PO_TYPE_ENUM 一致）。 */
    private Integer typeEnum;
}
