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
 * <p>按业务单号拉取同号全部供应请求行并装配为
 * {@link com.openhis.health.application.inventory.warehouse.inboundreceipt.response.GetInboundReceiptDetailResponse}。
 * 返回体中的 {@link com.openhis.health.application.inventory.warehouse.inboundreceipt.response.GetInboundReceiptDetailResponse#getRowVersion()}
 * 须在编辑前写入 {@link UpdateInboundReceiptRequest#setRowVersion(Long)}，以通过领域修订乐观锁门控。</p>
 *
 * <p>应用入口：
 * {@link com.openhis.health.application.inventory.warehouse.inboundreceipt.service.InventoryWarehouseInboundReceiptAppService#getInboundReceiptDetail(GetInboundReceiptDetailRequest)}。</p>
 */
@Getter
@Setter
public class GetInboundReceiptDetailRequest {

    /**
     * 进货业务单号；应用服务在门控阶段对首尾空白 {@code trim}。
     */
    private String busNo;
}
