/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.returnstockin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 药库/库房/科室/药房下拉选项（页初始化 IF-WHS-RSI-01 各 {@code *Options} 元素）。
 */
@Getter
@Setter
@Schema(description = "库位下拉选项")
public class WarehouseOptionDto {

    /** 库位 locationId。 */
    @Schema(description = "库位 locationId")
    private Long locationId;

    /** 展示名 displayName。 */
    @Schema(description = "展示名 displayName")
    private String displayName;

    /** 启用 active。 */
    @Schema(description = "启用 active")
    private Boolean active;
}
