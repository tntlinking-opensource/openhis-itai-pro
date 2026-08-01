/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.dailysettlement.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 门诊交账历史分页请求。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊交账历史分页请求", requiredProperties = {"submittedFrom", "submittedTo"})
public class PageOutpatientCashierSettlementHistoryRequest {

    @NotNull(message = "交账时间起不能为空")
    @JsonFormat(pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "交账提交时间起", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime submittedFrom;

    @NotNull(message = "交账时间止不能为空")
    @JsonFormat(pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "交账提交时间止", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime submittedTo;

    @Min(0)
    @Schema(description = "页码（从 0 起）")
    private Integer pageIndex = 0;

    @Min(1)
    @Max(200)
    @Schema(description = "页大小")
    private Integer pageSize = 20;
}
