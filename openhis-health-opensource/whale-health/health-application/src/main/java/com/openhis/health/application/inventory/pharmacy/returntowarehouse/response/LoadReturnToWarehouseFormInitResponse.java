/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.pharmacy.returntowarehouse.response;

import com.openhis.health.application.inventory.pharmacy.returntowarehouse.dto.SupplierOptionDto;
import com.openhis.health.application.inventory.pharmacy.returntowarehouse.dto.WarehouseOptionDto;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 药房退库页主数据初始化响应（源药房、目的药库、供应商下拉）。
 */
@Getter
@Setter
public class LoadReturnToWarehouseFormInitResponse {

    /** 当前用户可见的源药房库位选项。 */
    private List<WarehouseOptionDto> pharmacyLocationOptions;
    /** 当前用户可见的目的药库库位选项。 */
    private List<WarehouseOptionDto> warehouseLocationOptions;
    /** 可选供应商列表。 */
    private List<SupplierOptionDto> supplierOptions;
}
