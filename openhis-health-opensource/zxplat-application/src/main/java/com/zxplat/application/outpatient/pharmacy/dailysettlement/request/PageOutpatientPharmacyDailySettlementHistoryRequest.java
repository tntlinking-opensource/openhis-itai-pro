/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.pharmacy.dailysettlement.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 门诊药房交账历史分页请求。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊药房交账历史分页请求", requiredProperties = {"submittedFrom", "submittedTo"})
public class PageOutpatientPharmacyDailySettlementHistoryRequest {

    /** 交账提交时间起（按 submitted_at）。 */
    @NotNull(message = "交账时间起不能为空")
    @JsonFormat(pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "交账提交时间起", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime submittedFrom;

    /** 交账提交时间止（按 submitted_at）。 */
    @NotNull(message = "交账时间止不能为空")
    @JsonFormat(pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "交账提交时间止", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime submittedTo;

    /** 页码（从 0 起）。 */
    @Min(0)
    @Schema(description = "页码（从 0 起）")
    private Integer pageIndex = 0;

    /** 页大小。 */
    @Min(1)
    @Max(200)
    @Schema(description = "页大小")
    private Integer pageSize = 20;
}
