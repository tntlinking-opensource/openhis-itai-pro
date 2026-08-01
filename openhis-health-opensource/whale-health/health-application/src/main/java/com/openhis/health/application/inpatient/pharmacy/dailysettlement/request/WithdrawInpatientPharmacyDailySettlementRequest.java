/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.pharmacy.dailysettlement.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 撤回住院药房交账请求（IF-IP-PDC-04）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "撤回住院药房交账请求", requiredProperties = {"settlementId"})
public class WithdrawInpatientPharmacyDailySettlementRequest {

    /** 交账头主键。 */
    @NotNull(message = "交账头不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "交账头 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long settlementId;
}
