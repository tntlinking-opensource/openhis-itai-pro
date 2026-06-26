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

/**
 *
 * <p>当前无业务必填体字段：药房/药库位置、供应商、人员与筛选项下拉均由应用层按
 * {@link com.openhis.health.module.common.validator.CurrentManagedOrganizationIdsResolver} 与
 * {@link com.openhis.health.module.common.validator.CurrentManagedLocationIdsResolver} 机构与岗位管辖装配为
 * {@link com.openhis.health.application.inventory.warehouse.inboundreceipt.response.LoadInboundReceiptFormInitResponse}（见
 * {@link com.openhis.health.application.inventory.warehouse.inboundreceipt.mapper.InventoryWarehouseInboundReceiptMapper#toLoadInboundReceiptFormInitResponse}）。</p>
 *
 * <p>历史字段 {@link #organizationId} 若仍出现在请求 JSON 中将被<strong>忽略</strong>，不得用于授权或机构决策（B.10.11 会话真源）。</p>
 *
 * <p>应用入口：
 * {@link com.openhis.health.application.inventory.warehouse.inboundreceipt.service.InventoryWarehouseInboundReceiptAppService#loadInboundReceiptFormInit(LoadInboundReceiptFormInitRequest)}。</p>
 */
@Getter
@Setter
public class LoadInboundReceiptFormInitRequest {

    @Deprecated
    /** 机构主键。 */
    @Schema(description = "机构 organizationId")
    private Long organizationId;
}
