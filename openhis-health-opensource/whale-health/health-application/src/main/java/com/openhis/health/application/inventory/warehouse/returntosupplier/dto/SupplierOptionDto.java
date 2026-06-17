/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inventory.warehouse.returntosupplier.dto;

import lombok.Getter;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Setter;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
public class SupplierOptionDto {

    /** 供应商主键。 */
    @Schema(description = "供应商 supplierId")
    private Long supplierId;
    /** 供应商名称。 */
    @Schema(description = "供应商名称 supplierName")
    private String supplierName;
    /** 供应商类型。 */
    @Schema(description = "供应商类型 supplierType")
    private String supplierType;
}
