/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.returntosupplier.response;

import com.zxplat.application.inventory.warehouse.returntosupplier.dto.SupplierOptionDto;
import com.zxplat.application.inventory.warehouse.returntosupplier.dto.WarehouseOptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 药库退供应商页主数据初始化响应（IF-WHS-RT-01：管辖药库与启用供应商下拉）。
 */
@Getter
@Setter
@Schema(description = "退供应商页主数据初始化 IF-WHS-RT-01")
public class LoadReturnToSupplierFormInitResponse {

    /** 药库选项 warehouseOptions。 */
    @Schema(description = "药库选项 warehouseOptions")
    private List<WarehouseOptionDto> warehouseOptions;

    /** 供应商选项 supplierOptions。 */
    @Schema(description = "供应商选项 supplierOptions")
    private List<SupplierOptionDto> supplierOptions;
}
