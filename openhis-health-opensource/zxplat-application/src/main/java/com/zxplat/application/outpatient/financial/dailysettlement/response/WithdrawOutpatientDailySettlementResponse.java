/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.dailysettlement.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 撤回门诊交账响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "撤回门诊交账响应")
public class WithdrawOutpatientDailySettlementResponse {

    /** 交账头主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "交账头 ID")
    private Long settlementId;

    /** 交账单号。 */
    @Schema(description = "交账单号")
    private String busNo;
}
