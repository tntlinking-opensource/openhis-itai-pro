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
 * <p>将已提交的审批请求撤回至可编辑态（具体状态迁移由领域实现）；不涉及发放与库存侧写。
 * 业务单号在应用服务侧 {@code trim}。</p>
 *
 * <p>应用入口：
 * {@link com.openhis.health.application.inventory.warehouse.inboundreceipt.service.InventoryWarehouseInboundReceiptAppService#withdrawInboundReceiptApproval(WithdrawInboundReceiptApprovalRequest)}。</p>
 */
@Getter
@Setter
public class WithdrawInboundReceiptApprovalRequest {

    /**
     * 进货业务单号；首尾空白由服务端去除。
     */
    private String busNo;
}
