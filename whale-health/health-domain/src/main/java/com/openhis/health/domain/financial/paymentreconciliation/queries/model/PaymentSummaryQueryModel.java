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

package com.openhis.health.domain.financial.paymentreconciliation.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付汇总读模型：按经办人/合同与账单日区间的收退统计与分组字段。
 */
@Getter
@Setter
public class PaymentSummaryQueryModel {
    /** 经办人主键。 */
    private Long entererId;
    /** 经办人姓名（展示）。 */
    private String entererName;
    /** 合同号。 */
    private String contractNo;
    /** 合同名称（展示）。 */
    private String contractName;
    /** 总金额。 */
    private BigDecimal totalAmount;
    /** 支付笔数。 */
    private Long paymentCount;
    /** 成功笔数。 */
    private Long successCount;
    /** 退费笔数。 */
    private Long refundCount;
    /** 账单日起始（含）。 */
    private LocalDateTime billDateFrom;
    /** 账单日结束（含）。 */
    private LocalDateTime billDateTo;
    /** 分组字段名（与查询层约定）。 */
    private String groupByField;
}
