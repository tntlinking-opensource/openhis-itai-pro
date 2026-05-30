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
 * <p>修订成功后领域将同号单据置回待审批/待审核等状态（以领域为准）；本响应携带业务单号与
 * {@link com.openhis.health.domain.share.enums.SupplyStatus#getCode()} 持久化字符串（B.10.8.1）。
 * 客户端应使用 {@link com.openhis.health.application.inventory.warehouse.inboundreceipt.request.GetInboundReceiptDetailRequest}
 * 回读最新 {@code rowVersion} 后再发起后续提交或再次编辑。</p>
 */
@Getter
@Setter
public class UpdateInboundReceiptResponse {

    /**
     * 业务单号（与请求 {@code busNo} 一致，修订不改号）。
     */
    private String busNo;

    /**
     * 修订完成后的单据状态持久化码（{@link com.openhis.health.domain.share.enums.SupplyStatus#getCode()}）。
     */
    private String statusEnum;
}
