/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

/**
 * 收费定价/账单金额在库内统一为「元」的标度（与 {@code numeric(19,6)} 对齐）。
 *
 * <p>全局金钱契约见 {@code whale-doc/.cursor/skills/代码生成通用规范/references/monetary-amount-contract.md}；
 * 计算工具见 {@link com.openhis.health.domain.share.utils.MonetaryAmountSupport}。</p>
 */
public interface ChargeItemAmountScaleConstants {

    /**
     * 元金额写入定价行、账单、行级 API 时的统一小数位数（计算刻度；与库 {@code numeric(19,6)} 对齐）。
     */
    int CHARGE_MONETARY_SCALE = 6;

    /**
     * 结算/支付/发票等「应收总价」对外刻度：须在 6 位行金额加总后再四舍五入（**BR-MON-001**）。
     */
    int CHARGE_MONETARY_SETTLE_SCALE = 2;

    /**
     * 前端展示刻度（与 {@link #CHARGE_MONETARY_SETTLE_SCALE} 同为 2 位；仅 UI format，非入账权威）。
     */
    int CHARGE_MONETARY_DISPLAY_SCALE = 2;
}
