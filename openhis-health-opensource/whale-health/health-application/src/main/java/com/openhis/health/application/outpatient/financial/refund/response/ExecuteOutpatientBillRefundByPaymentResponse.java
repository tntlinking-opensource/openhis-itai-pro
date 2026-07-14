/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.refund.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 按原支付执行门诊账单退费响应（IF-REF-05）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "按原支付执行门诊账单退费响应")
public class ExecuteOutpatientBillRefundByPaymentResponse {

    /** 是否执行成功（业务字段）。 */
    @Schema(description = "是否执行成功")
    private boolean success;

    /** 逆向支付单主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 逆向支付单主键。 */
    @Schema(description = "逆向支付单主键")
    private Long reversePaymentId;

    /** 原支付主单主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 原支付主单主键。 */
    @Schema(description = "原支付主单主键")
    private Long originalPaymentId;

    /** 是否执行了半退再结算分支。 */
    @Schema(description = "是否执行了半退再结算分支")
    private boolean halfRefundExecuted;

    /** 医保正式结算是否已撤销（同步完成或无需撤销时为 true）。 */
    @Schema(description = "医保正式结算是否已撤销")
    private Boolean insuranceRevoked;
}
