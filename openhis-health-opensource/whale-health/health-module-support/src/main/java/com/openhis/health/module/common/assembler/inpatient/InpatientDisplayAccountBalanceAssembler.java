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
import com.openhis.health.domain.share.constants.InpatientPrepayConstants;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 住院场景患者栏「账户余额」展示装配（跨预交金、登记、护士站、医生站、款台等 IF 复用）。
 *
 * <p>口径（与 {@code AS-IP-FIN-002} §1.3、{@code DM-ADM-009} 一致）：</p>
 * <ul>
 *   <li>{@link Account#getBalance()}：个人现金户<strong>账户余额</strong>（预交金收退与结算扣已结费用后的库内值）。</li>
 *   <li>结算成功时写侧扣减本批 {@code SETTLED} 金额；出院全结少补/多退照旧调整余额。</li>
 *   <li><strong>待结算</strong>（{@code PENDING_SETTLEMENT}）：读侧从库内余额扣减后作为展示余额；写侧不在 ChargeItem 入账时回写 {@code balance}。</li>
 *   <li><strong>待收费</strong>（{@code PLANNED}）：不扣减展示余额，直至进入待结算。</li>
 *   <li>展示余额 = {@code balance} − 待结算合计；卡片/队列文案见 {@link InpatientAccountBalanceDisplayAssembler}（仅单一余额，不含「可用/待结算」拆分）。</li>
 * </ul>
 */
public final class InpatientDisplayAccountBalanceAssembler {

    private InpatientDisplayAccountBalanceAssembler() {
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
        return balance.subtract(pending)
            .setScale(InpatientPrepayConstants.PREPAY_AMOUNT_COMPARE_SCALE, RoundingMode.HALF_UP);
    }
}

