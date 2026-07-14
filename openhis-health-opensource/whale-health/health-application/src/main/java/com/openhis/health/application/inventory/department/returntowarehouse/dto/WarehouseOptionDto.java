/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.returntowarehouse.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 库位/科室药房/药库下拉选项行。
 */
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
