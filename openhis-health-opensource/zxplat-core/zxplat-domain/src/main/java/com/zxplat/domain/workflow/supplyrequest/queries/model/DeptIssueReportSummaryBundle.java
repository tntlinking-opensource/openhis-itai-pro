/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.supplyrequest.queries.model;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * 科室领用/退领报表汇总包：同一次 SupplyRequest 扫描产出净数量分桶与明细事实行。
 *
 * <p>供应用层汇总进价复用事实行，避免数量汇总后再分页二次扫描。</p>
 */
@Getter
@Setter
public class DeptIssueReportSummaryBundle {

    /** 按维度分桶后的净数量行。 */
    private List<DeptIssueReportSummaryRow> summaryRows = new ArrayList<>();

    /** 与汇总同窗的领用事实行。 */
    private List<DeptIssueReportLineQueryModel> lineRows = new ArrayList<>();
}
