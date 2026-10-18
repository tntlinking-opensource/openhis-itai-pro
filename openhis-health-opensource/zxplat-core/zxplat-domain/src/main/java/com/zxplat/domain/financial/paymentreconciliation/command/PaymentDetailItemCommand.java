/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.financial.paymentreconciliation.command;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付明细子命令。
 */
@Getter
@Setter
public class PaymentDetailItemCommand {
    /** 前驱主键。 */
    private Long predecessorId;
    /** 目标枚举编码。 */
    private String targetEnum;
    /** 账户主键。 */
    private Long accountId;
    /** 账户编码。 */
    private String accountCode;
    /** 支付枚举编码。 */
    private String payEnum;
    /** 支付等级枚举编码。 */
    private String payLevelEnum;
    /** 金额。 */
    private BigDecimal amount;
    /** 退回金额。 */
    private BigDecimal returnAmount;
    /** 收费金额。 */
    private BigDecimal chargeAmount;
    /** 支付交易号。 */
    private String payTransNo;
    /** 支付交易文本。 */
    private String payTransText;
    /** 支付交易日期。 */
    private LocalDateTime payTransDate;
    /** 之前balance。 */
    private BigDecimal beforeBalance;
    /** 之后balance。 */
    private BigDecimal afterBalance;
    /** 结果枚举编码。 */
    private String resultEnum;
}
