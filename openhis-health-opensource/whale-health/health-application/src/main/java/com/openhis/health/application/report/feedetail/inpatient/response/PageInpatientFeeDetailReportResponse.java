/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.feedetail.inpatient.response;

import com.openhis.health.application.outpatient.financial.feedetail.dto.OutpatientFeeDetailAmountSummaryDto;
import com.openhis.health.application.outpatient.financial.feedetail.dto.OutpatientFeeDetailEncounterBlockDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 住院费用明细报表明细分页响应（IF-RPT-IFD-03）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院费用明细报表明细分页响应")
public class PageInpatientFeeDetailReportResponse {

    /** 当前页就诊块列表。 */
    @Schema(description = "当前页就诊块")
    private List<OutpatientFeeDetailEncounterBlockDto> content;

    /** 命中就诊块总数。 */
    @Schema(description = "就诊块总数")
    private Long totalCount;

    /** 当前页码（1-based）。 */
    @Schema(description = "当前页码")
    private Integer pageNo;

    /** 页大小（就诊块数）。 */
    @Schema(description = "页大小")
    private Integer pageSize;

    /** 全量命中合计（非仅当前页）。 */
    @Schema(description = "全量金额合计")
    private OutpatientFeeDetailAmountSummaryDto summary;
}
