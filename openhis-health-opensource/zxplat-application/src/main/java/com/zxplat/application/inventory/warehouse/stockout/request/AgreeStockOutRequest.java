/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.stockout.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 同意药库出库单请求（IF-WHS-SO-08）。
 */
@Getter
@Setter
@Accessors(chain = true)
public class AgreeStockOutRequest {

    /** 出库业务单号。 */
    @Schema(description = "出库业务单号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String busNo;

    /** 医保契约 busNo。 */
    @Schema(description = "医保契约 busNo")
    private String contractBusNo;
}
