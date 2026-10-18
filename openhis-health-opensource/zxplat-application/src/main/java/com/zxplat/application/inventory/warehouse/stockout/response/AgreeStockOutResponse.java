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
 * 同意药库出库单响应（IF-WHS-SO-08）。
 */
@Getter
@Setter
@Accessors(chain = true)
public class AgreeStockOutResponse {

    /** 出库单号。 */
    @Schema(description = "出库单号")
    private String busNo;

    /** 出库单状态。 */
    @Schema(description = "出库单状态")
    private String statusEnum;

    /** 系统生成的对侧入库壳单号。 */
    @Schema(description = "系统生成的对侧入库壳单号")
    private String generatedPharmacyStockInBusNo;
}
