/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.stockout.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 编辑药库出库单响应（IF-WHS-SO-05）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "编辑药库出库单 IF-WHS-SO-05")
public class UpdateStockOutResponse {

    /** 业务单号。 */
    @Schema(description = "业务单号")
    private String busNo;
}
