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
import com.openhis.health.domain.share.enums.PaymentStatus;

/**
 * 按结算 ID 更新支付状态命令：同步支付状态枚举、医保结算串与渠道结果。
 */
@Getter
@Setter
public class UpdatePaymentStatusWithSettleIdsCommand {
    /** 支付单主键。 */
    private Long paymentId;
    /** 目标支付状态。 */
    private PaymentStatus statusEnum;
    /** 医保结算 ID 列表序列化串。 */
    private String ybSettleIds;
    /** 渠道或业务结果枚举编码。 */
    private String outcomeEnum;
}
