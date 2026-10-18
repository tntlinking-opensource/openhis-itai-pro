/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.inventoryopening.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 审批驳回期初单请求（IF-BAS-INV-OPN-09）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "驳回期初单", requiredProperties = {"busNo"})
public class RejectInventoryOpeningRequest {

    /** 业务单号。 */
    @Schema(description = "单号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String busNo;

    /** 驳回原因（可选）。 */
    @Schema(description = "驳回原因")
    private String reason;
}
