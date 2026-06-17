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
 * 住院场景患者栏「账户余额」展示装配（跨预交金、登记、护士站等 IF 复用）。
 *
 * <p>口径（与 {@code AS-IP-FIN-002} §1.3 一致）：</p>
 * <ul>
 *   <li>{@link Account#getBalance()}：个人现金户<strong>预交金收退净值</strong>，仅随预交金收/退变更。</li>
 *   <li><strong>费用占用额</strong>：{@link com.openhis.health.domain.share.enums.ChargeItemStatus#PENDING_SETTLEMENT}
 *   与 {@link com.openhis.health.domain.share.enums.ChargeItemStatus#SETTLED} 明细 {@code total_price} 之和
 *   （护士划价/执行入账待结算 + 中途/出院已结算，均为预交金口径下已花掉的钱）。</li>
 *   <li><strong>待收费</strong>（{@code PLANNED}，含住院医生站签发后）：<strong>不</strong>扣减展示余额，直至执行/划价进入待结算。</li>
 *   <li>对外展示余额 = 预交金净值 − 费用占用额；<strong>禁止</strong>将 Account.balance 直出为「还剩多少钱」。</li>
 * </ul>
 */
public final class InpatientDisplayAccountBalanceAssembler {

    private InpatientDisplayAccountBalanceAssembler() {
    }

    /**
     * 个人现金户预交金收退净值（无户或未落余额时按 0）。
     *
     * @param account 登记个人现金户，可 null
     * @return 预交金净值（仅内部计算或预交金退费等门禁，非患者栏展示）
     */
    public static BigDecimal resolvePersonalCashPrepayNet(Account account) {
        if (account == null || account.getBalance() == null) {
            return BigDecimal.ZERO;
        }
        return account.getBalance();
    }

    /**
     * 患者栏/队列展示的账户余额（动态计算，允许为负表示已透支口径）。
     *
     * @param account                 个人现金户；取 balance 参与计算，不对外直出
     * @param chargedOccupancyTotal   待结算 + 已结算费用合计（{@link com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService} 汇总）
     * @return 展示用 accountBalance
     */
    public static BigDecimal computeDisplayAccountBalance(
        Account account,
        BigDecimal chargedOccupancyTotal) {
        BigDecimal net = resolvePersonalCashPrepayNet(account);
        BigDecimal occupied = chargedOccupancyTotal;
        if (occupied == null) {
            occupied = BigDecimal.ZERO;
        }
        return net.subtract(occupied)
            .setScale(InpatientPrepayConstants.PREPAY_AMOUNT_COMPARE_SCALE, RoundingMode.HALF_UP);
    }
}
