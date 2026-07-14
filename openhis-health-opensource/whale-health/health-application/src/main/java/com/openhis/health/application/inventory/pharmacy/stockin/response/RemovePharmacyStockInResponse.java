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
 * 删除药房入库单响应（IF-PHA-IN-10）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "删除药房入库单 IF-PHA-IN-10")
public class RemovePharmacyStockInResponse {

    /** 是否成功。 */
    @Schema(description = "是否成功")
    private Boolean success;
}
