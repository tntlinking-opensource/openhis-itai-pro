/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 */

package com.openhis.health.application.inventory.pharmacy.returnupstream.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WarehouseOptionDto {

    /** 库位主键。 */
    private Long locationId;
    /** 展示名称。 */
    private String displayName;
    /** 是否启用。 */
    private Boolean active;
}
