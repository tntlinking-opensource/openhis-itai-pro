/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.feedetail.outpatient.response;

import com.zxplat.application.report.feedetail.dto.FeeDetailReportSummaryFooterDto;
import com.zxplat.application.report.feedetail.dto.FeeDetailReportSummaryRowDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 门诊费用明细报表类型净汇总响应（IF-RPT-OFD-02）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊费用明细报表汇总响应")
public class SummarizeOutpatientFeeDetailReportResponse {

    /** 按 fin_type 合并的净汇总行。 */
    @Schema(description = "汇总行")
    private List<FeeDetailReportSummaryRowDto> rows;

    /** 页脚合计（结算刻度）。 */
    @Schema(description = "页脚合计")
    private FeeDetailReportSummaryFooterDto footer;
}
