/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.devicedispense.queries.model;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * 耗材使用报表患者侧汇总包：同一次发放扫描产出净数量分桶与明细事实行。
 *
 * <p>供应用层汇总进售价复用事实行，避免数量汇总后再分页二次扫描。</p>
 */
@Getter
@Setter
public class ConsumableUsageDispenseSummaryBundle {

    /** 按维度分桶后的净数量行。 */
    private List<ConsumableUsageDispenseSummaryRow> summaryRows = new ArrayList<>();

    /** 与汇总同窗的发放事实行。 */
    private List<ConsumableUsageDispenseLineQueryModel> lineRows = new ArrayList<>();
}
