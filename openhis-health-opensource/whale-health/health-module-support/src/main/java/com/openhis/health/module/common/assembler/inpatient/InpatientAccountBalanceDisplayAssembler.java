/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.module.common.assembler.inpatient;

import com.openhis.health.domain.administration.account.entity.Account;
import com.openhis.health.domain.share.constants.InpatientAccountBalanceDisplayConstants;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.health.domain.share.constants.InpatientPrepayConstants;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 住院账户余额展示装配（展示余额 = 库内 {@code balance} − 待结算；卡片/队列仅输出单一余额文案）。
 */
public final class InpatientAccountBalanceDisplayAssembler {

    private InpatientAccountBalanceDisplayAssembler() {
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
        BigDecimal accountBalance = InpatientDisplayAccountBalanceAssembler.resolveAccountBalance(account);
        BigDecimal pending = normalizeAmount(pendingSettlementTotal);
        BigDecimal displayBalance = InpatientDisplayAccountBalanceAssembler.computeDisplayAccountBalance(
            account,
            pending);
        // 产品口径：各站点卡片/队列只展示余额数值，不再拆分「可用/待结算」文案。
        String displayMode = InpatientAccountBalanceDisplayConstants.DISPLAY_MODE_NORMAL;
        String summaryText = buildSummaryText(displayBalance);
        String subText = null;
        boolean lowBalanceWarning = isLowBalanceWarning(displayBalance);
        InpatientAccountBalanceDisplayResult result = new InpatientAccountBalanceDisplayResult();
        result.setAccountBalanceDisplayMode(displayMode);
        result.setPrepayNetBalance(accountBalance);
        result.setPendingSettlementTotal(pending);
        result.setAvailableBalance(displayBalance);
        result.setAccountBalance(displayBalance);
        result.setAccountBalanceSummaryText(summaryText);
        result.setAccountBalanceSubText(subText);
        result.setLowBalanceWarning(lowBalanceWarning);
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

    private static BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ZERO.setScale(
                InpatientAccountBalanceDisplayConstants.AMOUNT_DISPLAY_SCALE,
                RoundingMode.HALF_UP);
        }
        return amount.setScale(
            InpatientAccountBalanceDisplayConstants.AMOUNT_DISPLAY_SCALE,
            RoundingMode.HALF_UP);
    }

    private static String buildSummaryText(BigDecimal displayBalance) {
        BigDecimal balance = displayBalance;
        if (balance == null) {
            balance = BigDecimal.ZERO;
        }
        // 正负号区分余额方向，标签统一为「余额」，不再使用「欠费」等前缀。
        return formatAmount(balance) + " 元";
    }

    private static String formatAmount(BigDecimal amount) {
        BigDecimal normalized = normalizeAmount(amount);
        return normalized.toPlainString();
    }
}

