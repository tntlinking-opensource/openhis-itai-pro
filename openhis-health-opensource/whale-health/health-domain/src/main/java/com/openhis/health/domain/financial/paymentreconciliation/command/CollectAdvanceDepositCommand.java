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
import com.openhis.health.domain.share.enums.PaymentKind;

import java.math.BigDecimal;
import java.util.List;

/**
 * 收取预交金命令：为指定就诊/患者登记预交款，带支付渠道与明细分录。
 */
@Getter
@Setter
public class CollectAdvanceDepositCommand {
    /** 就诊主键。 */
    private Long encounterId;
    /** 患者主键。 */
    private Long patientId;
    /** 经办人主键。 */
    private Long entererId;
    /** 实收金额。 */
    private BigDecimal tenderedAmount;
    /** 展示金额。 */
    private BigDecimal displayAmount;
    /** 支付大类。 */
    private PaymentKind kindEnum;
    /** 支付渠道枚举编码。 */
    private String paymentEnum;
    /** 支付明细分录。 */
    private List<PaymentDetailItemCommand> paymentDetails;
}
