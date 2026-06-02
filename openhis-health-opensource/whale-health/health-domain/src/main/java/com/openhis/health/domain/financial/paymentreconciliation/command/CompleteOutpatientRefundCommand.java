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
 * 完成门诊退费命令：写入退费结果、医保结算 ID 集合与账单日期，关闭退费流程。
 */
@Getter
@Setter
public class CompleteOutpatientRefundCommand {
    /** 支付单主键。 */
    private Long paymentId;
    /** 结果枚举编码。 */
    private String outcomeEnum;
    /** 医保结算 ID 列表序列化串（与领域约定格式一致）。 */
    private String ybSettleIds;
    /** 账单日期。 */
    private LocalDateTime billDate;
}
