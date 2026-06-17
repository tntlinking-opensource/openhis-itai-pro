/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inventory.pharmacy.returnupstream.response;

import com.openhis.health.application.inventory.pharmacy.returnupstream.dto.SupplierOptionDto;
import com.openhis.health.application.inventory.pharmacy.returnupstream.dto.WarehouseOptionDto;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class LoadReturnUpstreamFormInitResponse {

    /** pharmacyLocationOptions 字段。 */
    private List<WarehouseOptionDto> pharmacyLocationOptions;
    /** warehouseLocationOptions 字段。 */
    private List<WarehouseOptionDto> warehouseLocationOptions;
    /** 可选供应商列表。 */
    private List<SupplierOptionDto> supplierOptions;
}
