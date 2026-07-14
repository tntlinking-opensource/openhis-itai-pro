/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.inventorydetail.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 批量停供入参（IF-INV-DTL-08）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "批量停供 IF-INV-DTL-08", requiredProperties = {"inventoryItemIds"})
public class RetireInventoryItemsRequest {
    /** 库存项主键列表。 */
    @Schema(description = "库存项主键列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> inventoryItemIds;
}
