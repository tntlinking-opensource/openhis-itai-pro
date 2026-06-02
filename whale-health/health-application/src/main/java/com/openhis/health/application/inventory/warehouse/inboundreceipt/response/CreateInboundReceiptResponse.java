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
 * <p>领域取号并落库后返回新业务单号；{@link #statusEnum} 为创建完成时刻的
 * {@link com.openhis.health.domain.share.enums.SupplyStatus#getCode()} 持久化字符串（B.10.8.1），
 * 通常为待审批态。客户端可继续用 {@code busNo} 拉详情或提交审批。</p>
 */
@Getter
@Setter
public class CreateInboundReceiptResponse {

    /**
     * 新建分配的业务单号（与 {@code wkf_supply_request.bus_no} 一致）。
     */
    private String busNo;

    /**
     * 创建完成后的单据状态持久化码。
     */
    private String statusEnum;
}
