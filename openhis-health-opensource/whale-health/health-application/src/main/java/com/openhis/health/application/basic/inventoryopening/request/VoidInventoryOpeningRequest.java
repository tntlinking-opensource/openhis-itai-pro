/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.inventoryopening.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 作废当前有效期初单请求（IF-BAS-INV-OPN-15）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "作废期初单", requiredProperties = {"busNo"})
public class VoidInventoryOpeningRequest {

    /** 业务单号（须为当前 AGREE）。 */
    @Schema(description = "单号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String busNo;

    /** 作废原因（可选）。 */
    @Schema(description = "作废原因")
    private String reason;
}
