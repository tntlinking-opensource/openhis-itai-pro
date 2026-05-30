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

import lombok.Getter;
import lombok.Setter;

/**
 *
 * <p>领域 {@code agreeRequest} 成功后，应用层继续执行入库发放、批号维定价、库存入账与追溯码写入；
 * 本 DTO 仅承载「编排链是否整体成功」布尔值；任一步失败时由应用服务抛出
 * {@link com.openhis.whale.core.application.exception.BusinessException}（如 {@code WHS_IN_104}），通常不会返回 {@code success=false}。</p>
 */
@Getter
@Setter
public class AgreeInboundReceiptResponse {

    /**
     * 同意及后续入库编排是否整体成功；与 {@link com.openhis.health.application.inventory.warehouse.inboundreceipt.mapper.InventoryWarehouseInboundReceiptMapper#toAgreeInboundReceiptResponse(boolean)} 入参一致。
     */
    private Boolean success;
}
