/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.nurseworkload.response;

import com.openhis.health.application.report.nurseworkload.dto.NurseExecutionSummaryRowDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * 护士诊疗执行汇总查询出参（IF-RPT-NWL-02）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "护士诊疗执行汇总 IF-RPT-NWL-02")
public class SummarizeNurseExecutionWorkloadResponse {

    /** 汇总行。 */
    @Schema(description = "汇总行")
    private List<NurseExecutionSummaryRowDto> rows;

    /** 合计执行次数。 */
    @Schema(description = "合计执行次数")
    private Long totalExecutionCount;

    /** 合计售价。 */
    @Schema(description = "合计售价")
    private BigDecimal totalSaleAmount;

    /** 合计进价（诊疗无进价，恒 null）。 */
    @Schema(description = "合计进价")
    private BigDecimal totalPurchaseAmount;
}
