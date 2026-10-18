/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.traceassignment.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-IP-TRC-03：追溯码匹配预览行。
 */
@Data
@Accessors(chain = true)
@Schema(description = "追溯码匹配预览行")
public class TraceCodeMatchPreviewDto {

    /** 汇总组键。 */
    @Schema(description = "汇总组键")
    private String groupKey;

    /** 命中追溯码列表。 */
    @Schema(description = "命中追溯码列表")
    private List<String> matchedTraceCodes;

    /** 药品 ID。 */
    @Schema(description = "药品 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long medicationId;

    /** 批号。 */
    @Schema(description = "批号")
    private String lotNumber;
}
