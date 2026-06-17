/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inventory.warehouse.returnfromdownstream.response;

import com.openhis.health.application.inventory.warehouse.returnfromdownstream.dto.SupplierOptionDto;
import com.openhis.health.application.inventory.warehouse.returnfromdownstream.dto.WarehouseOptionDto;
import lombok.Getter;
import lombok.Setter;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
public class LoadReturnFromDownstreamFormInitResponse {

    /** 药库下拉选项。 */
    @Schema(description = "药库选项 warehouseOptions")
    private List<WarehouseOptionDto> warehouseOptions;
    /** 科室下拉选项。 */
    @Schema(description = "科室选项 departmentOptions")
    private List<WarehouseOptionDto> departmentOptions;
    /** 药房下拉选项。 */
    @Schema(description = "药房选项 pharmacyOptions")
    private List<WarehouseOptionDto> pharmacyOptions;
    /** 供应商下拉选项。 */
    @Schema(description = "供应商选项 supplierOptions")
    private List<SupplierOptionDto> supplierOptions;
}
