/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.supplyrequest.queries.model;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/**
 * 科室领用/退领报表净汇总行。
 */
@Getter
@Setter
public class DeptIssueReportSummaryRow {

    /** 维度键字符串。 */
    private String dimensionKey;

    /** 汇总维度码。 */
    private String summaryDimension;

    /** 领用科室组织主键（DEPARTMENT 维）。 */
    private Long organizationId;

    /** 科室药房主键（PHARMACY 维）。 */
    private Long purposeLocationId;

    /** 品规主键（ITEM 维）。 */
    private Long itemId;

    /** 净数量：领用为正、退领为负。 */
    private BigDecimal netQuantity;

    /** 命中行数。 */
    private long lineCount;
}
