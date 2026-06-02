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

import java.time.LocalDateTime;

/**
 *
 * <p>将待编辑/草稿态进货单提交进入审批流；具体目标状态由 {@code SupplyRequestDomainService#submitApproval} 状态机决定。
 * 业务单号在应用服务侧 {@code trim}；须满足同号行均可提交的门禁（由领域校验）。</p>
 *
 * <p>应用入口：
 * {@link com.openhis.health.application.inventory.warehouse.inboundreceipt.service.InventoryWarehouseInboundReceiptAppService#submitInboundReceiptApproval(SubmitInboundReceiptApprovalRequest)}。</p>
 */
@Getter
@Setter
public class SubmitInboundReceiptApprovalRequest {

    /**
     * 进货业务单号；首尾空白由服务端去除后写入领域提交项。
     */
    private String busNo;

    /**
     * 申请业务时钟；可空时由领域命令或默认值填充，用于与申请时间线对齐。
     */
    private LocalDateTime applyClock;
}
