/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.department.returntowarehouse.response;

import com.zxplat.application.inventory.department.returntowarehouse.dto.SupplierOptionDto;
import com.zxplat.application.inventory.department.returntowarehouse.dto.WarehouseOptionDto;
import com.zxplat.framework.core.application.dto.OptionDto;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 科室退库页主数据初始化响应（源科室药房、目的药库、供应商与人员下拉）。
 */
@Getter
@Setter
public class LoadReturnToWarehouseFormInitResponse {

    /** 当前用户可见的源科室药房库位选项。 */
    private List<WarehouseOptionDto> departmentPharmacyLocationOptions;
    /** 当前用户可见的目的药库库位选项。 */
    private List<WarehouseOptionDto> warehouseLocationOptions;
    /** 可选供应商列表。 */
    private List<SupplierOptionDto> supplierOptions;
    /** 药师下拉（平台角色 {@code ROLE_PHARMACIST}，按会话机构收窄）。 */
    private List<OptionDto> pharmacistOptions;
    /** 库管下拉（平台角色 {@code ROLE_WAREHOUSE}，按会话机构收窄）。 */
    private List<OptionDto> warehouseKeeperOptions;
}
