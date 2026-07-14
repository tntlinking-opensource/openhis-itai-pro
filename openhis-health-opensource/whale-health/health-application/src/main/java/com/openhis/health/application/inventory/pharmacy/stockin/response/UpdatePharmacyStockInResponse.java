/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.pharmacy.stockin.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 编辑药房入库单响应（IF-PHA-IN-05）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "编辑药房入库单 IF-PHA-IN-05")
public class UpdatePharmacyStockInResponse {

    /** 业务单号。 */
    @Schema(description = "业务单号")
    private String busNo;
}
