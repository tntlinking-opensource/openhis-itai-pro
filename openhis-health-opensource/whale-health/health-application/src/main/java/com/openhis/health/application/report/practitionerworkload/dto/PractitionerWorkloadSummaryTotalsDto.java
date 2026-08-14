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
 * 工作量汇总页脚合计 DTO（IF-RPT-WKL-02/03；DD-报表中心-进售价口径）。
 *
 * <p>金额列仅售价/进价合计（账单行加和）；本轮不含应收/实收。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "工作量汇总合计")
public class PractitionerWorkloadSummaryTotalsDto {

    /** 售价合计（账单 total_price 加和；结算刻度）。 */
    @Schema(description = "售价合计")
    private BigDecimal saleAmountTotal;

    /** 进价合计（批号进价包装锚加和；诊疗无进价不累加；结算刻度）。 */
    @Schema(description = "进价合计")
    private BigDecimal purchaseAmountTotal;

    /** 挂号净人次合计（与 AS-RPT-007 同口径：一就诊一次，退号不计入）。 */
    @Schema(description = "挂号净人次合计")
    private Integer registrationNetCountTotal;

    /** 接诊人数合计。 */
    @Schema(description = "接诊人数合计")
    private Integer encounterCountTotal;

    /** 处方数量合计（按处方号去重；同方多行计 1）。 */
    @Schema(description = "处方数量合计")
    private Integer prescriptionCountTotal;

    /** 诊疗项目总数量合计。 */
    @Schema(description = "诊疗项目总数量合计")
    private Integer activityItemCountTotal;
}
