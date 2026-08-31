/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.pharmacy.prescriptionreview.request;

import com.openhis.health.application.outpatient.pharmacy.prescriptionreview.dto.BatchCreateReviewItemDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 批量创建点评请求（IF-PRV-04）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "批量创建点评请求", requiredProperties = {"items", "reasonableFlag"})
public class BatchCreateReviewsRequest {

    /** 待点评处方列表（非空、处方号去重）。 */
    @Schema(description = "待点评处方列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<BatchCreateReviewItemDto> items;

    /** 共用是否合理。 */
    @Schema(description = "是否合理", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean reasonableFlag;

    /** 共用原因；不合理时必填。 */
    @Schema(description = "原因（不合理时必填）")
    private String reason;

    /** 共用备注（可选）。 */
    @Schema(description = "备注")
    private String remark;
}
