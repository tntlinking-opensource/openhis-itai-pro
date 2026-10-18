/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.inventorydetail.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 批量恢复供应入参（IF-INV-DTL-09）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "批量恢复供应 IF-INV-DTL-09", requiredProperties = {"inventoryItemIds"})
public class ResumeInventoryItemsRequest {

    @NotEmpty(message = "库存项主键列表不能为空")
/** 库存项主键列表。 */
@Schema(description = "库存项主键列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> inventoryItemIds;
}
