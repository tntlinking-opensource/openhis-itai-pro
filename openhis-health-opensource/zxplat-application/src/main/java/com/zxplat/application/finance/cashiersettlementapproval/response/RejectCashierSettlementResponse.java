/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.finance.cashiersettlementapproval.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 交账审批驳回响应（IF-CS-APR-04）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "交账审批驳回响应")
public class RejectCashierSettlementResponse {

    /** 交账头主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "交账头主键")
    private Long settlementId;

    /** 驳回原因回显；可空。 */
    @Schema(description = "驳回原因")
    private String reason;
}
