/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.finance.cashiersettlementapproval.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 交账审批驳回请求（IF-CS-APR-04）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "交账审批驳回请求", requiredProperties = {"settlementId"})
public class RejectCashierSettlementRequest {

    /** 交账头主键。 */
    @NotNull(message = "交账头标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "交账头主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long settlementId;

    /** 驳回原因；可空。 */
    @Schema(description = "驳回原因")
    private String reason;
}
