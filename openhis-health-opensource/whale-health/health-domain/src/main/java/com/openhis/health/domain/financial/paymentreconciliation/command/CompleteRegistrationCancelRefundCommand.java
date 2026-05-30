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

package com.openhis.health.domain.financial.paymentreconciliation.command;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 退号联动退费完成命令（挂号域 IF-REG-10，仅整单全退、无产品半退）。
 *
 * <p>一次性收口原支付主单与逆向退款单，并发布<strong>唯一</strong> {@code OutpatientRefundCompletedEvent}
 *（聚合主键为逆向单），供日结等下游消费。与门诊账单退费执行（半退/保留链）编排分离。</p>
 */
@Getter
@Setter
public class CompleteRegistrationCancelRefundCommand {

    /** 原挂号支付主单主键。 */
    private Long originalPaymentId;

    /** 退号逆向退款单主键（{@code relation_id} 指向原单）。 */
    private Long reversePaymentId;

    /** 结果枚举编码（可选）。 */
    private String outcomeEnum;

    /** 医保结算 ID 列表序列化串（可选）。 */
    private String ybSettleIds;

    /** 账单日期（可选；默认由领域服务取当前时间）。 */
    private LocalDateTime billDate;
}
