/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.financial.cashiersettlement.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 交账历史列表行。
 */
@Getter
@Setter
public class CashierSettlementHistoryItemQueryModel {

    /** 交账头主键。 */
    private Long settlementId;

    /** 交账单号。 */
    private String busNo;

    /** 交账类型 code。 */
    private String typeEnum;

    /** 交账提交时刻。 */
    private LocalDateTime submittedAt;

    /** 业务窗起。 */
    private LocalDateTime dateFrom;

    /** 业务窗止。 */
    private LocalDateTime dateTo;

    /** 本批人员快照。 */
    private String practitionerIds;

    /** 提交人。 */
    private Long submitterId;

    /** 审批人。 */
    private Long approverId;

    /** 审批时间。 */
    private LocalDateTime approvedAt;

    /** 交账科室。 */
    private Long orgId;

    /** 交账药房。 */
    private Long locationId;

    /** 挂载笔数。 */
    private Integer paymentCount;

    /** 列表金额快照。 */
    private BigDecimal totalIncome;

    /**
     * 是否可按挂载现算重打（有挂载笔数即为 true）。
     */
    private boolean canReprintFromMounts;

    /** 交账状态 code。 */
    private String statusEnum;
}
