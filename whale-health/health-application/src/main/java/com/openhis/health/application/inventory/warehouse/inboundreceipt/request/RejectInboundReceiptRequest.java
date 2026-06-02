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
 * <p>驳回仅变更供应请求状态与原因，不触发放与库存。{@link #rejectReason} 为展示与审计用文本或约定编码字符串，
 * 由 Mapper 映射为领域驳回命令的「变更原因」载荷；若未来存在独立字典枚举，仍须以 {@code getCode()} 持久化（B.10.8.1）。</p>
 *
 * <p>应用入口：
 * {@link com.openhis.health.application.inventory.warehouse.inboundreceipt.service.InventoryWarehouseInboundReceiptAppService#rejectInboundReceipt(RejectInboundReceiptRequest)}。</p>
 */
@Getter
@Setter
public class RejectInboundReceiptRequest {

    /**
     * 进货业务单号；应用服务在门控阶段对首尾空白 {@code trim}，再下推领域查询。
     */
    private String busNo;

    /**
     * 审批人主键；可空，空时由应用层回落为当前操作人（与同意/提交路径一致）。
     */
    private Long approverId;

    /**
     * 审批业务时钟；可空时由领域或命令默认值填充，用于审计时间线对齐。
     */
    private LocalDateTime approvalClock;

    /**
     * 驳回原因：须非空白；写入领域驳回项的变更原因字段，供列表/详情展示与追溯。
     */
    private String rejectReason;
}
