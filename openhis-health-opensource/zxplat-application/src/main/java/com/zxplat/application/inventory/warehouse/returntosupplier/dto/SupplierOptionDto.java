/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.returntosupplier.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 供应商下拉选项（页初始化 {@code supplierOptions} 元素，IF-WHS-RT-01）。
 */
@Getter
@Setter
@Schema(description = "供应商下拉选项")
public class SupplierOptionDto {

    /** 供应商 supplierId。 */
    @Schema(description = "供应商 supplierId")
    private Long supplierId;

    /** 供应商名称 supplierName。 */
    @Schema(description = "供应商名称 supplierName")
    private String supplierName;

    /** 供应商类型 supplierType。 */
    @Schema(description = "供应商类型 supplierType")
    private String supplierType;
}
