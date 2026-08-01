/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.financial.dailysettlement.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 确认住院交账响应（IF-IP-DCL-03）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "确认住院交账响应")
public class SubmitInpatientDailySettlementResponse {

    /** 交账头主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "交账头 ID")
    private Long settlementId;

    /** 交账单号。 */
    @Schema(description = "交账单号")
    private String busNo;

    /** 挂载条数。 */
    @Schema(description = "挂载条数")
    private Integer paymentCount;

    /** 交账头状态 code（提交后一般为 SUBMITTED）。 */
    @Schema(description = "交账头状态枚举 code")
    private String statusEnum;

    /** 截止后未交账条数（仅提醒，不拦提交）。 */
    @Schema(description = "截止后未交账条数（仅提醒）")
    private Long gapSoftWarnCount;
}
