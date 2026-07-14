/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.financial.paymentreconciliation.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付对账明细读模型：单条渠道记账/退款明细及账户余额前后值。
 */
@Getter
@Setter
public class PaymentRecDetailQueryModel {
    /** 明细主键。 */
    private Long id;
    /** 所属对账/支付聚合主键。 */
    private Long reconciliationId;
    /** 前置明细主键（冲正或关联链）。 */
    private Long predecessorId;
    /** 记账目标类型枚举编码。 */
    private String targetEnum;
    /** 账户主键。 */
    private Long accountId;
    /** 账户编码展示。 */
    private String accountCode;
    /** 支付渠道枚举编码。 */
    private String payEnum;
    /** 支付层级或分段枚举编码。 */
    private String payLevelEnum;
    /** 发生金额。 */
    private BigDecimal amount;
    /** 退款金额。 */
    private BigDecimal returnAmount;
    /** 计费金额（与 amount 口径由读服务解释）。 */
    private BigDecimal chargeAmount;
    /** 渠道交易号。 */
    private String payTransNo;
    /** 渠道原始回执文本摘要。 */
    private String payTransText;
    /** 渠道交易时间。 */
    private LocalDateTime payTransDate;
    /** 交易前账户余额。 */
    private BigDecimal beforeBalance;
    /** 交易后账户余额。 */
    private BigDecimal afterBalance;
    /** 渠道结果枚举编码。 */
    private String resultEnum;
}
