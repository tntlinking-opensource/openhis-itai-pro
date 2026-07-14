/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.inventorydetail.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 批量恢复供应出参（IF-INV-DTL-09）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "批量恢复供应 IF-INV-DTL-09")
public class ResumeInventoryItemsResponse {
    /** 成功更新行数。 */
    @Schema(description = "成功更新行数")
    private Integer affectedRows;
}
