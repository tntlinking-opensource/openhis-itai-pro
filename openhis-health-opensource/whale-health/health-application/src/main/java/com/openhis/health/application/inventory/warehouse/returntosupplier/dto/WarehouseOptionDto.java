/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 */

package com.openhis.health.application.inventory.warehouse.returntosupplier.dto;

import lombok.Getter;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Setter;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
public class WarehouseOptionDto {

    /** 库位主键。 */
    @Schema(description = "库位 locationId")
    private Long locationId;
    /** 展示名称。 */
    @Schema(description = "展示名 displayName")
    private String displayName;
    /** 是否启用。 */
    @Schema(description = "启用 active")
    private Boolean active;
}
