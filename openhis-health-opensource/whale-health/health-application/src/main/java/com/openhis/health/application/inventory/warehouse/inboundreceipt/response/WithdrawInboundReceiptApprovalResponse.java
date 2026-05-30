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
 * {@link com.openhis.health.application.inventory.warehouse.inboundreceipt.mapper.InventoryWarehouseInboundReceiptMapper#toWithdrawInboundReceiptApprovalResponse}
 * 装配；{@code statusEnum} 非空时为 {@link com.openhis.health.domain.share.enums.SupplyStatus#getCode()}（B.10.8.1），取自撤回后同号首行回读。
 * 缺失时客户端应重新拉取详情以对齐乐观锁与展示。</p>
 */
@Getter
@Setter
public class WithdrawInboundReceiptApprovalResponse {

    /**
     * 撤回操作是否成功落库。
     */
    private Boolean success;

    /**
     * 撤回后的单据状态持久化码；可为 null（见类说明）。
     */
    private String statusEnum;
}
