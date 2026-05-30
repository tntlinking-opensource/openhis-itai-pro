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
 * 编辑订货单请求。
 *
 * <p>位置类型枚举由后端按 {@code purposeLocationId} 推导，请求体不传（B.10.8.1）。</p>
 */
@Getter
@Setter
public class UpdatePurchaseOrderRequest {

    /** 既有订货单号。 */
    private String busNo;
    /** 入库目的药库（必填）。 */
    private Long purposeLocationId;
    /**
     * 供应类别：{@link com.openhis.health.domain.share.enums.SupplyCategory#getCode()}；可 blank，缺省库存供应。
     */
    private String categoryEnum;
    /** 供应商。 */
    private Long supplierId;
    /** 经手人医务人员主键（前端下拉选择）。 */
    private Long practitionerId;
    /** 明细全量替换。 */
    private List<PurchaseOrderLineDto> lines;
    /** 乐观锁版本（可选）。 */
    private Long rowVersion;
}
