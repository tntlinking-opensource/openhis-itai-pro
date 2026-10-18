/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.refund.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 退费确认窗渠道实退明细（IF-REF-05）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "退费渠道实退明细")
public class RefundPaymentDetailSubmitDto {

    /** 退款渠道编码。 */
    @NotBlank(message = "payChannelCode 不能为空")
    @Schema(description = "退款渠道编码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String payChannelCode;

    /** 本渠道实退金额。 */
    @NotNull(message = "amount 不能为空")
    /** 本渠道实退金额。 */
    @Schema(description = "本渠道实退金额", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal amount;

    /** 原支付明细交易号（医保行须带原 setl_id）。 */
    @Schema(description = "原支付明细交易号（医保 setl_id）")
    private String payTransNo;

    /** 原支付明细交易文本。 */
    @Schema(description = "原支付明细交易文本")
    private String payTransText;

    /** 原支付明细交易时间。 */
    @Schema(description = "原支付明细交易时间")
    private java.time.LocalDateTime payTransDate;

    /** 渠道结果枚举（沿用原明细终态，禁止落 DRAFT）。 */
    @Schema(description = "渠道结果枚举")
    private String resultEnum;

    /** 原支付明细账户主键。 */
    @Schema(description = "账户主键")
    private Long accountId;

    /** 原支付明细账户编码。 */
    @Schema(description = "账户编码")
    private String accountCode;
}
