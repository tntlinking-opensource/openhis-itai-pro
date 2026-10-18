/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.financial.prepay.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 退预交金响应（IF-IP-PREP-04）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "退预交金响应")
public class RefundPrepayAdvanceDepositResponse {

    /** 退款支付流水 ID。 */
    @Schema(description = "退款支付流水 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long paymentId;

    /** 结算后账户余额。 */
    @Schema(description = "结算后账户余额（展示口径，动态计算）")
    private BigDecimal accountBalance;
}
