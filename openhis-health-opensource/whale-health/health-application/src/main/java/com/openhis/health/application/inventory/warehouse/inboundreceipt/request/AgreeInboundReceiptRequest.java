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
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 *
 * <p>领域 {@code agreeRequest} 仅负责供应请求状态迁移为已同意；应用层随后编排入库发放、批号维定价、库存入账与追溯码写入。
 * 业务单号在应用服务侧 {@code trim}；{@link #approverId} 可空时回落为当前操作人。</p>
 *
 * <p>应用入口：
 * {@link com.openhis.health.application.inventory.warehouse.inboundreceipt.service.InventoryWarehouseInboundReceiptAppService#agreeInboundReceipt(AgreeInboundReceiptRequest)}。</p>
 */
@Getter
@Setter
public class AgreeInboundReceiptRequest {

    /**
     * 进货业务单号；应用服务门控非空并对首尾空白 {@code trim}。
     */
    private String busNo;

    /**
     * 审批人主键；可空，空时由应用层回落为当前操作人（单岗工作站可不传）。
     */
    private Long approverId;

    /**
     * 审批业务时钟；可空时由领域或命令默认值填充，用于审计与状态迁移时间线。
     */
    private LocalDateTime approvalClock;

    /** @deprecated CHS 模块启用即上传，此开关不再生效。 */
    @Deprecated
    @Schema(description = "医保上报 medicareUploadEnabled")
    private Boolean medicareUploadEnabled;

    /** 医保契约 busNo；未传时回落 {@code openhis.chs.inventory.default-contract-bus-no} 或租户首条医保契约。 */
    private String contractBusNo;
}
