/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.practitionerworkload.response;

import com.openhis.health.application.report.practitionerworkload.dto.PractitionerWorkloadDetailLineDto;
import com.openhis.health.application.report.practitionerworkload.dto.PractitionerWorkloadSummaryTotalsDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 科室/医生工作量明细分页响应（IF-RPT-WKL-03）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "科室医生工作量明细分页响应")
public class PagePractitionerWorkloadDetailResponse {

    /** 明细行。 */
    @Schema(description = "明细行")
    private List<PractitionerWorkloadDetailLineDto> content;

    /** 总条数。 */
    @Schema(description = "总条数")
    private Long totalCount;

    /** 当前页码。 */
    @Schema(description = "当前页码")
    private Integer pageNo;

    /** 页大小。 */
    @Schema(description = "页大小")
    private Integer pageSize;

    /** 汇总 digest。 */
    @Schema(description = "汇总 digest")
    private PractitionerWorkloadSummaryTotalsDto summaryTotals;
}
