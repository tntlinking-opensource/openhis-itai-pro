/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.finance.cashiersettlementapproval.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 交账审批通过请求（IF-CS-APR-03）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "交账审批通过请求", requiredProperties = {"settlementId"})
public class ApproveCashierSettlementRequest {

    /** 交账头主键。 */
    @NotNull(message = "交账头标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "交账头主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long settlementId;
}
