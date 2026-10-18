/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.financial.settle.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 医保出院预交金原路退计划项（预结算回传，供确认前 Win 退货）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医保出院预交金原路退计划项")
public class InpatientPrepayRefundPlanItemDto {

    /** 原预交金收款支付单 ID。 */
    @Schema(description = "原预交金收款支付单 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long originalPaymentId;

    /** 原收款渠道码（PaymentChannel）。 */
    @Schema(description = "原收款渠道码")
    private String payChannelCode;

    /** 本项拟退金额。 */
    @Schema(description = "本项拟退金额")
    private BigDecimal amount;

    /** 是否须银联 Win 退货回传后再本地退。 */
    @Schema(description = "是否须银联 Win 退货回传")
    private Boolean requiresClientRefund;
}
