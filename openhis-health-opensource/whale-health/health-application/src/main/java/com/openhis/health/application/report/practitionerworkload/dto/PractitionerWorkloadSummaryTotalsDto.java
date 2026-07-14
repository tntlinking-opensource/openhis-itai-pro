/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.practitionerworkload.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 工作量汇总页脚合计 DTO（IF-RPT-WKL-02/03）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "工作量汇总合计")
public class PractitionerWorkloadSummaryTotalsDto {

    /** 应收合计。 */
    @Schema(description = "应收合计")
    private BigDecimal receivableAmountTotal;

    /** 实收合计。 */
    @Schema(description = "实收合计")
    private BigDecimal actualReceivedAmountTotal;

    /** 成本合计。 */
    @Schema(description = "成本合计")
    private BigDecimal costAmountTotal;

    /** 净收入合计。 */
    @Schema(description = "净收入合计")
    private BigDecimal netIncomeAmountTotal;

    /** 挂号净笔数合计。 */
    @Schema(description = "挂号净笔数合计")
    private Integer registrationNetCountTotal;

    /** 接诊人数合计。 */
    @Schema(description = "接诊人数合计")
    private Integer encounterCountTotal;

    /** 处方数量合计。 */
    @Schema(description = "处方数量合计")
    private Integer prescriptionCountTotal;

    /** 诊疗项目总数量合计。 */
    @Schema(description = "诊疗项目总数量合计")
    private Integer activityItemCountTotal;
}
