/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.inventoryopening.response;

import com.openhis.health.application.basic.inventoryopening.dto.InventoryOpeningProductRowDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 期初选品分页响应（IF-BAS-INV-OPN-11）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "期初选品分页响应")
public class PageInventoryOpeningsProductsResponse {

    /** 总条数。 */
    @Schema(description = "总条数")
    private Long total;

    /** 选品行。 */
    @Schema(description = "选品行")
    private List<InventoryOpeningProductRowDto> records;
}
