/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.dailysettlement.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 加载门诊交账原单打印快照请求。
 */
@Data
@Accessors(chain = true)
@Schema(description = "加载门诊交账原单打印快照请求", requiredProperties = {"settlementId"})
public class LoadOutpatientCashierSettlementVoucherRequest {

    @NotNull(message = "交账头不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "交账头主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long settlementId;
}
