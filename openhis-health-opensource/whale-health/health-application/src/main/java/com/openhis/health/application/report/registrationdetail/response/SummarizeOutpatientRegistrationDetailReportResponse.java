/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.registrationdetail.response;

import com.openhis.health.application.report.registrationdetail.dto.OutpatientRegistrationDetailSummaryRowDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 门诊挂号明细报表汇总响应（IF-RPT-ORG-02）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊挂号明细报表汇总响应")
public class SummarizeOutpatientRegistrationDetailReportResponse {

    /** 净汇总行（科室或医生 × 号源）。 */
    @Schema(description = "汇总行")
    private List<OutpatientRegistrationDetailSummaryRowDto> rows;

    /** 净挂号笔数合计。 */
    @Schema(description = "净挂号笔数合计")
    private Long totalNetRegistrationCount;
}

