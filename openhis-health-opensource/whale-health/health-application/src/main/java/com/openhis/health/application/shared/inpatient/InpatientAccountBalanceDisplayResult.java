/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.inpatient;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 住院账户余额展示读模型（队列/摘要共用；写侧多退少补与反结逻辑不变）。
 */
@Data
@Accessors(chain = true)
public class InpatientAccountBalanceDisplayResult {

    /** 展示模式：{@link com.openhis.health.domain.share.constants.InpatientAccountBalanceDisplayConstants}。 */
    private String accountBalanceDisplayMode;

    /** 预交金净值（个人现金户 {@code Account.balance}）。 */
    private BigDecimal prepayNetBalance;

    /** 待结算费用合计（{@code PENDING_SETTLEMENT}；明细字段，卡片不单独展示）。 */
    private BigDecimal pendingSettlementTotal;

    /** 展示余额（库内余额 − 待结算；与 {@link #accountBalance} 一致）。 */
    private BigDecimal availableBalance;

    /**
     * 对外兼容字段：与 {@link #availableBalance} 一致，供既有 {@code accountBalance} 消费方使用。
     */
    private BigDecimal accountBalance;

    /** 主展示文案（单一余额，如 {@code 500.00 元} 或 {@code -50.00 元}）。 */
    private String accountBalanceSummaryText;

    /** 副展示文案（当前产品恒为空，保留字段兼容）。 */
    private String accountBalanceSubText;

    /** 低余额预警（展示余额低于护士站阈值）。 */
    private boolean lowBalanceWarning;
}
