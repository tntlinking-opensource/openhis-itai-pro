/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.inpatient;

import com.zxplat.domain.administration.account.entity.Account;
import com.zxplat.domain.share.constants.InpatientAccountBalanceDisplayConstants;
import com.zxplat.domain.share.constants.InpatientNurseStationConstants;
import com.zxplat.domain.share.utils.MonetaryAmountSupport;

import java.math.BigDecimal;

/**
 * 住院账户余额展示装配（跨预交金、登记、护士站、医生站、款台等 IF 复用）。
 *
 * <p>口径（与 {@code AS-IP-FIN-002} §1.3、{@code DM-ADM-009} 一致）：</p>
 * <ul>
 *   <li>{@link Account#getBalance()}：个人现金户<strong>账户余额</strong>（预交金收退与结算扣已结费用后的库内值）。</li>
 *   <li><strong>待结算</strong>（{@code PENDING_SETTLEMENT}）：读侧从库内余额扣减后作为展示余额。</li>
 *   <li>展示余额 = {@code balance} − 待结算合计；卡片/队列仅输出单一余额文案。</li>
 * </ul>
 */
public final class InpatientAccountBalanceDisplayAssembler {

    /** 工具类禁止实例化。 */
    private InpatientAccountBalanceDisplayAssembler() {
    }

    /**
     * 个人现金户库内账户余额（无户或未落余额时按 0）。
     *
     * @param account 登记个人现金户，可 null
     * @return 账户余额（{@code adm_account.balance}）
     */
    public static BigDecimal resolveAccountBalance(Account account) {
        if (account == null || account.getBalance() == null) {
            return BigDecimal.ZERO;
        }
        return account.getBalance();
    }

    /**
     * 个人现金户库内余额别名（预交金退费可退上限等门禁复用）。
     *
     * @param account 登记个人现金户，可 null
     * @return 账户余额
     */
    public static BigDecimal resolvePersonalCashPrepayNet(Account account) {
        return resolveAccountBalance(account);
    }

    /**
     * 展示余额（允许为负表示欠费口径）。
     *
     * @param account                个人现金户
     * @param pendingSettlementTotal 待结算费用合计（不含已结算）
     * @return 展示余额 = 库内余额 − 待结算
     */
    public static BigDecimal computeDisplayAccountBalance(
        Account account,
        BigDecimal pendingSettlementTotal) {
        BigDecimal balance = resolveAccountBalance(account);
        BigDecimal pending = pendingSettlementTotal;
        if (pending == null) {
            pending = BigDecimal.ZERO;
        }
        return MonetaryAmountSupport.scaleDisplay(balance.subtract(pending));
    }

    /**
     * 组装账户余额展示读模型。
     *
     * @param account                    个人现金户；可 null
     * @param pendingSettlementTotal     待结算合计；可 null 按 0
     * @param settledSettlementTotal     已结算合计（保留入参兼容批量预取；不参与展示文案）
     * @param reversedPendingResettle    是否为反结后待重新结算（保留入参兼容；展示仍输出单一余额）
     * @return 展示读模型（含文案与低余额预警）
     */
    public static InpatientAccountBalanceDisplayResult assemble(
        Account account,
        BigDecimal pendingSettlementTotal,
        BigDecimal settledSettlementTotal,
        boolean reversedPendingResettle) {
        BigDecimal accountBalance = resolveAccountBalance(account);
        BigDecimal pending = MonetaryAmountSupport.scaleDisplay(pendingSettlementTotal);
        BigDecimal displayBalance = computeDisplayAccountBalance(account, pending);
        String displayMode = InpatientAccountBalanceDisplayConstants.DISPLAY_MODE_NORMAL;
        String summaryText = buildSummaryText(displayBalance);
        InpatientAccountBalanceDisplayResult result = new InpatientAccountBalanceDisplayResult();
        result.setAccountBalanceDisplayMode(displayMode);
        result.setPrepayNetBalance(accountBalance);
        result.setPendingSettlementTotal(pending);
        result.setAvailableBalance(displayBalance);
        result.setAccountBalance(displayBalance);
        result.setAccountBalanceSummaryText(summaryText);
        result.setAccountBalanceSubText(null);
        result.setLowBalanceWarning(isLowBalanceWarning(displayBalance));
        return result;
    }

    /**
     * 展示余额是否低于护士站低余额预警阈值。
     *
     * @param displayBalance 展示余额（库内余额 − 待结算）
     * @return true 表示需预警
     */
    public static boolean isLowBalanceWarning(BigDecimal displayBalance) {
        if (displayBalance == null) {
            return false;
        }
        return displayBalance.compareTo(InpatientNurseStationConstants.LOW_BALANCE_WARNING_THRESHOLD) < 0;
    }

    /**
     * 组装主展示文案（单一余额 + 「元」后缀）。
     *
     * @param displayBalance 展示余额
     * @return 格式化后的主文案
     */
    private static String buildSummaryText(BigDecimal displayBalance) {
        BigDecimal balance = displayBalance;
        if (balance == null) {
            balance = BigDecimal.ZERO;
        }
        return formatAmount(balance) + " 元";
    }

    /**
     * 金额展示格式化（统一 scale 后转 plain string）。
     *
     * @param amount 原始金额
     * @return 展示用数字字符串
     */
    private static String formatAmount(BigDecimal amount) {
        BigDecimal normalized = MonetaryAmountSupport.scaleDisplay(amount);
        return normalized.toPlainString();
    }
}
