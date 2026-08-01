/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.financial.cashiersettlement.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 交账头读模型（DM-FIN-003 SDD §4.6.1，对应 {@code findHeaderById}）。
 */
@Getter
@Setter
public class CashierSettlementHeaderQueryModel {

    /** 交账头主键。 */
    private Long settlementId;

    /** 交账单号。 */
    private String busNo;

    /** 交账类型 code。 */
    private String typeEnum;

    /** 审批状态 code。 */
    private String statusEnum;

    /** 交账科室。 */
    private Long orgId;

    /** 交账药房。 */
    private Long locationId;

    /** 业务窗起。 */
    private LocalDateTime dateFrom;

    /** 业务窗止。 */
    private LocalDateTime dateTo;

    /** 挂载笔数快照。 */
    private Integer paymentCount;

    /** 列表金额快照。 */
    private BigDecimal totalIncome;

    /** 窗内人员快照（逗号分隔）。 */
    private String practitionerIds;

    /** 提交人。 */
    private Long submitterId;

    /** 交账提交时间。 */
    private LocalDateTime submittedAt;

    /** 审批人。 */
    private Long approverId;

    /** 审批时间。 */
    private LocalDateTime approvedAt;
}
