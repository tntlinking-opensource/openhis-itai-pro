/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.inpatient;

import java.math.BigDecimal;

/**
 * 将 {@link InpatientAccountBalanceDisplayResult} 写入各应用层 DTO 的共用字段（读侧展示）。
 */
public final class InpatientAccountBalanceDisplayDtoApplier {

    /** 工具类禁止实例化。 */
    private InpatientAccountBalanceDisplayDtoApplier() {
    }

    /**
     * 账户余额展示字段写入载体（应用层 DTO 实现）。
     */
    public interface AccountBalanceDisplayTarget {

        /**
         * 设置展示模式。
         *
         * @param accountBalanceDisplayMode 展示模式 code
         * @return 当前目标，便于链式调用
         */
        AccountBalanceDisplayTarget setAccountBalanceDisplayMode(String accountBalanceDisplayMode);

        /**
         * 设置预交金净值。
         *
         * @param prepayNetBalance 预交金净值
         * @return 当前目标，便于链式调用
         */
        AccountBalanceDisplayTarget setPrepayNetBalance(BigDecimal prepayNetBalance);

        /**
         * 设置待结算合计。
         *
         * @param pendingSettlementTotal 待结算合计
         * @return 当前目标，便于链式调用
         */
        AccountBalanceDisplayTarget setPendingSettlementTotal(BigDecimal pendingSettlementTotal);

        /**
         * 设置可用余额。
         *
         * @param availableBalance 可用余额
         * @return 当前目标，便于链式调用
         */
        AccountBalanceDisplayTarget setAvailableBalance(BigDecimal availableBalance);

        /**
         * 设置兼容展示余额。
         *
         * @param accountBalance 与可用余额一致
         * @return 当前目标，便于链式调用
         */
        AccountBalanceDisplayTarget setAccountBalance(BigDecimal accountBalance);

        /**
         * 设置主展示文案。
         *
         * @param accountBalanceSummaryText 主文案
         * @return 当前目标，便于链式调用
         */
        AccountBalanceDisplayTarget setAccountBalanceSummaryText(String accountBalanceSummaryText);

        /**
         * 设置副展示文案。
         *
         * @param accountBalanceSubText 副文案
         * @return 当前目标，便于链式调用
         */
        AccountBalanceDisplayTarget setAccountBalanceSubText(String accountBalanceSubText);

        /**
         * 设置低余额预警。
         *
         * @param lowBalanceWarning 是否预警
         * @return 当前目标，便于链式调用
         */
        AccountBalanceDisplayTarget setLowBalanceWarning(Boolean lowBalanceWarning);
    }

    /**
     * 将展示读模型写入目标 DTO。
     *
     * @param target 目标 DTO
     * @param source 展示读模型；null 时跳过
     */
    public static void apply(AccountBalanceDisplayTarget target, InpatientAccountBalanceDisplayResult source) {
        if (target == null || source == null) {
            return;
        }
        target.setAccountBalanceDisplayMode(source.getAccountBalanceDisplayMode());
        target.setPrepayNetBalance(source.getPrepayNetBalance());
        target.setPendingSettlementTotal(source.getPendingSettlementTotal());
        target.setAvailableBalance(source.getAvailableBalance());
        target.setAccountBalance(source.getAccountBalance());
        target.setAccountBalanceSummaryText(source.getAccountBalanceSummaryText());
        target.setAccountBalanceSubText(source.getAccountBalanceSubText());
        target.setLowBalanceWarning(source.isLowBalanceWarning());
    }
}
