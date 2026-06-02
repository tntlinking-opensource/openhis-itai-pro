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

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付明细子命令。
 */
@Getter
@Setter
public class PaymentDetailItemCommand {
    /** 前驱主键。 */
    private Long predecessorId;
    /** 目标枚举编码。 */
    private String targetEnum;
    /** 账户主键。 */
    private Long accountId;
    /** 账户编码。 */
    private String accountCode;
    /** 支付枚举编码。 */
    private String payEnum;
    /** 支付等级枚举编码。 */
    private String payLevelEnum;
    /** 金额。 */
    private BigDecimal amount;
    /** 退回金额。 */
    private BigDecimal returnAmount;
    /** 收费金额。 */
    private BigDecimal chargeAmount;
    /** 支付交易号。 */
    private String payTransNo;
    /** 支付交易文本。 */
    private String payTransText;
    /** 支付交易日期。 */
    private LocalDateTime payTransDate;
    /** 之前balance。 */
    private BigDecimal beforeBalance;
    /** 之后balance。 */
    private BigDecimal afterBalance;
    /** 结果枚举编码。 */
    private String resultEnum;
}
