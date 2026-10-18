/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.traceassignment.response;

import com.zxplat.application.inpatient.nursestation.traceassignment.dto.TraceCodeMatchPreviewDto;
import com.zxplat.application.inpatient.nursestation.traceassignment.dto.TraceCodeResolveFailItemDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-IP-TRC-03：批量解析追溯码出参。
 */
@Data
@Accessors(chain = true)
@Schema(description = "批量解析追溯码出参")
public class ResolveTraceCodesForPatientResponse {

    /** 匹配预览列表。 */
    @Schema(description = "匹配预览列表")
    private List<TraceCodeMatchPreviewDto> matches;

    /** 失败码明细。 */
    @Schema(description = "失败码明细")
    private List<TraceCodeResolveFailItemDto> failItems;
}
