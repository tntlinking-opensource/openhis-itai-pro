/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.stocktake.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 审批驳回盘点单入参（IF-WHS-ST-09）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "审批驳回盘点 IF-WHS-ST-09")
public class RejectStocktakeRequest {

    /** 业务单号 busNo。 */
    @Schema(description = "业务单号 busNo", requiredMode = Schema.RequiredMode.REQUIRED)
    private String busNo;

    /** 驳回原因码 changeReasonCode。 */
    @Schema(description = "驳回原因码 changeReasonCode")
    private String changeReasonCode;

    /** 驳回原因 changeReason。 */
    @Schema(description = "驳回原因 changeReason")
    private String changeReason;
}
