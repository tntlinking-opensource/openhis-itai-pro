/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inventory.warehouse.returntosupplier.request;

import lombok.Getter;
import lombok.Setter;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
public class LoadReturnToSupplierFormInitRequest {

    /** 机构主键。 */
    @Schema(description = "机构 organizationId")
    private Long organizationId;
}
