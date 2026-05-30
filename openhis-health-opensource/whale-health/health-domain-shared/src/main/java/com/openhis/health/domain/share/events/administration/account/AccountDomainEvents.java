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

package com.openhis.health.domain.share.events.administration.account;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

public final class AccountDomainEvents {
    /**
     * 处理逻辑。
     * @return 返回结果
     */
    private AccountDomainEvents() {
    }

    /**
     * 账户已开立事件。账户插入成功时发布。
     */
    @Getter
    @Setter
    public static class AccountOpenedEvent extends DomainEvent {
        /** 就诊标识。 */
        private Long encounterId;
        /** 账户类型编码（字典）。 */
        private String typeCode;
        /** 患者标识。 */
        private Long patientId;
        /** 患者姓名快照（账户展示用）。 */
        private String patientName;
        /** 开立时初始余额。 */
        private BigDecimal balanceAmount;

        public AccountOpenedEvent() { super(); }

        /**
         * 向后兼容构造器；新字段通过 setter 注入。
         *
         * @param accountId  账户标识（聚合根 ID）
         * @param encounterId 就诊标识
         * @param typeCode   账户类型编码
         */
        public AccountOpenedEvent(Long accountId, Long encounterId, String typeCode) {
            super(accountId, AggregateTypeConstants.ACCOUNT);
            this.encounterId = encounterId;
            this.typeCode = typeCode;
        }
    }

    /**
     * 账户余额已变更事件。余额字段变化时发布。
     */
    @Getter
    @Setter
    public static class AccountBalanceEditedEvent extends DomainEvent {
        /** 变更前余额。 */
        private BigDecimal oldBalance;
        /** 变更后余额。 */
        private BigDecimal newBalance;
        /** 就诊标识（读模型关联投影用）。 */
        private Long encounterId;

        public AccountBalanceEditedEvent() { super(); }

        /**
         * 向后兼容构造器；encounterId 通过 setter 注入。
         *
         * @param accountId  账户标识（聚合根 ID）
         * @param oldBalance 变更前余额
         * @param newBalance 变更后余额
         */
        public AccountBalanceEditedEvent(Long accountId, BigDecimal oldBalance, BigDecimal newBalance) {
            super(accountId, AggregateTypeConstants.ACCOUNT);
            this.oldBalance = oldBalance;
            this.newBalance = newBalance;
        }
    }
}
