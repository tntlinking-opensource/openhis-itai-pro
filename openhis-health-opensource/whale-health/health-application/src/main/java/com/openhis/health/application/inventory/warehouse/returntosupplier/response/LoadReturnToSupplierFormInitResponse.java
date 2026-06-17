/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inventory.warehouse.returntosupplier.response;

import com.openhis.health.application.inventory.warehouse.returntosupplier.dto.SupplierOptionDto;
import com.openhis.health.application.inventory.warehouse.returntosupplier.dto.WarehouseOptionDto;
import lombok.Getter;
import lombok.Setter;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
public class LoadReturnToSupplierFormInitResponse {

    /** 药库下拉选项。 */
    @Schema(description = "药库选项 warehouseOptions")
    private List<WarehouseOptionDto> warehouseOptions;
    /** 供应商下拉选项。 */
    @Schema(description = "供应商选项 supplierOptions")
    private List<SupplierOptionDto> supplierOptions;
}
