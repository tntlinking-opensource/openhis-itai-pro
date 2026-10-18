/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.financial.paymentreconciliation.queries.model;

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
