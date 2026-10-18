/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.pharmacy.prescriptionreview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 批量创建点评子项（IF-PRV-04）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "批量创建点评子项", requiredProperties = {"prescriptionNo"})
public class BatchCreateReviewItemDto {

    /** 处方号（点评业务号由领域 PRV 日序自生成，前端不传）。 */
    @Schema(description = "处方号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String prescriptionNo;
}
