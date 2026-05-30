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

/**
 * 关闭单笔支付结果命令：将某条支付明细标记为终态并写入渠道交易号/结果码。
 */
@Getter
@Setter
public class CloseSinglePaymentResultCommand {
    /** 支付单主键。 */
    private Long paymentId;
    /** 支付明细主键。 */
    private Long detailId;
    /** 渠道结果枚举编码。 */
    private String resultEnum;
    /** 第三方支付流水号。 */
    private String payTransNo;
}
