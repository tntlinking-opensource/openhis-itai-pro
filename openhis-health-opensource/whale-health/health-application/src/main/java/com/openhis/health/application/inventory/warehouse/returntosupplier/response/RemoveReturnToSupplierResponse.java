/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inventory.warehouse.returntosupplier.response;

import lombok.Getter;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Setter;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
public class RemoveReturnToSupplierResponse {

    /** 是否成功。 */
    @Schema(description = "是否成功 success")
    private Boolean success;
}
