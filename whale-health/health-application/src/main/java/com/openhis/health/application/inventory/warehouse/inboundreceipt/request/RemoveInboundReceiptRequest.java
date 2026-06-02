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

import lombok.Getter;
import lombok.Setter;

/**
 *
 * <p>删除语义由底座软删与领域门禁共同约束：仅允许在规定状态下执行；业务单号在应用服务侧 {@code trim} 后下推。</p>
 *
 * <p>应用入口：
 * {@link com.openhis.health.application.inventory.warehouse.inboundreceipt.service.InventoryWarehouseInboundReceiptAppService#removeInboundReceipt(RemoveInboundReceiptRequest)}。</p>
 */
@Getter
@Setter
public class RemoveInboundReceiptRequest {

    /**
     * 待删除的进货业务单号（首尾空白由服务端去除）；须已通过「药库进货单」类型门禁。
     */
    private String busNo;
}
