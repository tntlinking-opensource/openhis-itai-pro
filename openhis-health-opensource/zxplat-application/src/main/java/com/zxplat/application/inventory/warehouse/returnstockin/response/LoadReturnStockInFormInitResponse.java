/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.returnstockin.response;

import com.zxplat.application.inventory.warehouse.returnstockin.dto.SupplierOptionDto;
import com.zxplat.application.inventory.warehouse.returnstockin.dto.WarehouseOptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 退库入库页主数据初始化响应（IF-WHS-RSI-01：岗位可见药库/科室/药房与启用供应商下拉）。
 */
@Getter
@Setter
@Schema(description = "退库入库页主数据初始化 IF-WHS-RSI-01")
public class LoadReturnStockInFormInitResponse {

    /** 药库选项 warehouseOptions。 */
    @Schema(description = "药库选项 warehouseOptions")
    private List<WarehouseOptionDto> warehouseOptions;

    /** 科室选项 departmentOptions。 */
    @Schema(description = "科室选项 departmentOptions")
    private List<WarehouseOptionDto> departmentOptions;

    /** 药房选项 pharmacyOptions。 */
    @Schema(description = "药房选项 pharmacyOptions")
    private List<WarehouseOptionDto> pharmacyOptions;

    /** 供应商选项 supplierOptions。 */
    @Schema(description = "供应商选项 supplierOptions")
    private List<SupplierOptionDto> supplierOptions;
}
