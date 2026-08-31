/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.medication.medicationdispense.queries.model;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * 发药报表患者侧汇总包：同一次发放事实扫描产出净数量分桶与 slip 行。
 *
 * <p>供应用层汇总进售价时复用 slip，避免数量汇总后再按页二次 findAll。</p>
 */
@Getter
@Setter
public class MedicationDispenseReportSummaryBundle {

    /** 按维度分桶后的净数量行。 */
    private List<MedicationDispenseReportSummaryRow> summaryRows = new ArrayList<>();

    /** 与汇总同窗的发放事实行（顺序与仓储返回一致）。 */
    private List<MedicationDispenseSlipQueryModel> slipRows = new ArrayList<>();
}
