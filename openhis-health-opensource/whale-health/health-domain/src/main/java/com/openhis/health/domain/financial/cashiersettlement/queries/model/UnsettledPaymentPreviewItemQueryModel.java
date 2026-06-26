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

package com.openhis.health.domain.financial.cashiersettlement.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 未交账 payment 预览行。
 */
@Getter
@Setter
public class UnsettledPaymentPreviewItemQueryModel {

    /** 支付流水 ID。 */
    private Long paymentId;

    /** 支付单号。 */
    private String paymentNo;

    /** 业务 kind code。 */
    private String kindEnum;

    /** 支付状态 code。 */
    private String statusEnum;

    /** 收费员（enterer）。 */
    private Long entererId;

    /** 结算时间。 */
    private LocalDateTime billDate;

    /** 收退方向：PAY / UN_PAY。 */
    private String paymentEnum;

    /** 逆向单关联原支付主键；非空表示退费/反结逆向流水。 */
    private Long relationId;

    /** 实收金额。 */
    private BigDecimal tenderedAmount;

    /** 退还金额。 */
    private BigDecimal returnedAmount;

    /** 净额。 */
    private BigDecimal netAmount;

    /** 费用性质合同业务号（{@code fin_payment_reconciliation.contract_no}）。 */
    private String contractNo;
}
