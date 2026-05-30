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
 * 门诊账单退费执行：将原支付主单从退款处理中收口为已退款（静默写库，不发退费完成事件）。
 *
 * <p>与 {@link CompleteOutpatientRefundCommand} 配合：本命令仅更新原单持久化；退费完成事件由逆向单
 * {@code completeOutpatientRefund} 发布一次。<strong>退号</strong>请使用
 * {@link CompleteRegistrationCancelRefundCommand}，勿复用本命令。</p>
 */
@Getter
@Setter
public class FinalizeOriginalOutpatientPaymentCommand {

    /** 原支付主单主键。 */
    private Long originalPaymentId;

    /** 结果枚举编码（可选）。 */
    private String outcomeEnum;

    /** 医保结算 ID 列表序列化串（可选）。 */
    private String ybSettleIds;

    /** 账单日期（可选；默认由领域服务取当前时间）。 */
    private LocalDateTime billDate;
}
