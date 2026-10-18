/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.feedetail.response;

import com.zxplat.application.outpatient.financial.feedetail.dto.OutpatientFeeDetailAmountSummaryDto;
import com.zxplat.application.outpatient.financial.feedetail.dto.OutpatientFeeDetailEncounterBlockDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 分页查询门诊费用明细响应（IF-OP-FDT-02）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "分页查询门诊费用明细响应")
public class PageOutpatientFeeDetailResponse {

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
