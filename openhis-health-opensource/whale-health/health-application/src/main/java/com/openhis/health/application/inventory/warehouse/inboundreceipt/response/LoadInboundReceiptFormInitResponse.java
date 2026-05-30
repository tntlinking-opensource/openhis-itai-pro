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

import com.openhis.whale.core.application.dto.OptionDto;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 *
 * <p>由 {@link com.openhis.health.application.inventory.warehouse.inboundreceipt.mapper.InventoryWarehouseInboundReceiptMapper#toLoadInboundReceiptFormInitResponse}
 * 及若干 {@code toOptionDtoList*} 装配；与 {@link com.openhis.health.application.inventory.warehouse.inboundreceipt.request.LoadInboundReceiptFormInitRequest} 成对使用。</p>
 *
 * <p>下拉项统一为 {@link OptionDto}（B.10.5）：{@code sort} 为排序权重，{@code value} 为前端绑定值（字符串），{@code label} 为展示文案。</p>
 *
 * <p>药房与药库位置分两个列表返回，便于前端分控件绑定；各自独立：无岗位分配时均为启用态全量（不按 organization_id 过滤）；有分配且分配位置与该形态有交则只返回管辖内，否则该列表仍返回该形态启用全量。</p>
 */
@Getter
@Setter
public class LoadInboundReceiptFormInitResponse {

    private List<OptionDto> pharmacyLocationOptions;

    private List<OptionDto> warehouseLocationOptions;

    private List<OptionDto> supplierOptions;

    private List<OptionDto> pharmacistOptions;

    private List<OptionDto> warehouseKeeperOptions;

    /**
     * {@code productTableName} 白名单一致。
     */
    private List<OptionDto> inboundItemTableTypeOptions;

    private List<OptionDto> statusOptions;
}
