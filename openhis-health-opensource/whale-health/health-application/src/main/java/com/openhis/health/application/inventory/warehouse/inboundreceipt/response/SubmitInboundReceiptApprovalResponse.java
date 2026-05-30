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
 * <p>{@link #success} 与 {@link #statusEnum} 由
 * {@link com.openhis.health.application.inventory.warehouse.inboundreceipt.mapper.InventoryWarehouseInboundReceiptMapper#toSubmitInboundReceiptApprovalResponse}
 * 装配；{@code statusEnum} 非空时为 {@link com.openhis.health.domain.share.enums.SupplyStatus#getCode()}（B.10.8.1），取自提交后同号首行回读。
 * 若回读失败则可能不下发 {@code statusEnum}，客户端应以重新拉取详情为准。</p>
 */
@Getter
@Setter
public class SubmitInboundReceiptApprovalResponse {

    /**
     * 提交编排是否成功；领域校验失败时可能为 {@code false} 或改由应用层抛业务异常。
     */
    private Boolean success;

    /**
     * 提交后的单据状态持久化码；可为 null（见类说明）。
     */
    private String statusEnum;
}
