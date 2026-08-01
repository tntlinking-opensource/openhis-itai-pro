/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.supplyrequest.queries.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * 科室领用/退领报表明细行（单行 SupplyRequest）。
 */
@Getter
@Setter
public class DeptIssueReportLineQueryModel {

    /** 供应请求行主键。 */
    private Long supplyRequestId;

    /** 业务单号。 */
    private String busNo;

    /** 类型码：DEPT_ISSUE / DEPT_ISSUE_RETURN。 */
    private String typeEnum;

    /** 状态码（已同意）。 */
    private String statusEnum;

    /** 品规表名。 */
    private String itemTable;

    /** 品规主键。 */
    private Long itemId;

    /** 行数量（退领存正数退回量，读侧净额由汇总取负）。 */
    private BigDecimal itemQuantity;

    /** 单位码。 */
    private String unitCode;

    /** 科室药房主键。 */
    private Long purposeLocationId;

    /**
     * 领用科室（行政 Organization 主键；库列 {@code consuming_organization_id}）。
     *
     * <p>新单落库；历史可空，读侧可回落药房 {@code Location.organization_id}。</p>
     */
    private Long consumingOrganizationId;

    /** 批号（可空）。 */
    private String lotNumber;

    /** 业务时间（优先 approval_time，其次 occurrence_time）。 */
    private LocalDateTime businessTime;

    /** 单价（落库，可空）。 */
    private BigDecimal price;

    /** 行总价（落库，可空）。 */
    private BigDecimal totalPrice;
}
