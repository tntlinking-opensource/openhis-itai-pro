/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.inventorydetail.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 批量停供出参（IF-INV-DTL-08）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "批量停供 IF-INV-DTL-08")
public class RetireInventoryItemsResponse {
    /** 成功更新行数。 */
    @Schema(description = "成功更新行数")
    private Integer affectedRows;
}
