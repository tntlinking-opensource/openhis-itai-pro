/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.pharmacy.stockin.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 药房入库单详情请求（IF-PHA-IN-03）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "药房入库单详情 IF-PHA-IN-03", requiredProperties = {"busNo"})
public class GetPharmacyStockInDetailRequest {

    /** 业务单号。 */
    @NotBlank(message = "busNo 不能为空")
    @Schema(description = "业务单号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String busNo;
}
