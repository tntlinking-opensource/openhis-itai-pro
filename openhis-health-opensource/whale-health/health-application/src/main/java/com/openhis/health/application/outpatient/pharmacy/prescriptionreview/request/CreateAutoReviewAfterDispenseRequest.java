/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.pharmacy.prescriptionreview.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 发药完成后自动点评请求（IF-PRV-06）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "发药完成后自动点评请求", requiredProperties = {"prescriptionNos", "dispenserId", "autoReviewEnabled"})
public class CreateAutoReviewAfterDispenseRequest {

    /** 本次核发完成涉及的处方号（去重后非空；开关关闭时可空）。 */
    @Schema(description = "处方号列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<String> prescriptionNos;

    /** 发药人（点评人）；通常=当前发药操作员。 */
    @Schema(description = "发药人主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long dispenserId;

    /** 自动点评开关；false 时直接空成功。 */
    @Schema(description = "是否启用自动点评", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean autoReviewEnabled;
}
