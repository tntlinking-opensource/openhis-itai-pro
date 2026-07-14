/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.stockout.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 删除药库出库单响应（IF-WHS-SO-10）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "删除药库出库单 IF-WHS-SO-10")
public class RemoveStockOutResponse {

    /** 是否成功。 */
    @Schema(description = "是否成功")
    private Boolean success;
}
