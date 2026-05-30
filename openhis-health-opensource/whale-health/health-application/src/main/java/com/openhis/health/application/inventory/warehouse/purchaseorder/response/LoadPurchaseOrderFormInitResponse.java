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

package com.openhis.health.application.inventory.warehouse.purchaseorder.response;

import com.openhis.health.application.inventory.warehouse.purchaseorder.dto.SupplierOptionDto;
import com.openhis.health.application.inventory.warehouse.purchaseorder.dto.WarehouseOptionDto;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 加载订货页初始化响应。
 */
@Getter
@Setter
/**
 * LoadPurchaseOrderFormInitResponse。
 */
public class LoadPurchaseOrderFormInitResponse {

    /**
     * warehouseOptions。
     */
    private List<WarehouseOptionDto> warehouseOptions;
    /**
     * supplierOptions。
     */
    private List<SupplierOptionDto> supplierOptions;

    /** 左侧列表筛选用「全部药库」；{@code locationId} 为占位（约定 0），非真实 Location。 */
    /**
     * allWarehousesOption。
     */
    private WarehouseOptionDto allWarehousesOption;
}
