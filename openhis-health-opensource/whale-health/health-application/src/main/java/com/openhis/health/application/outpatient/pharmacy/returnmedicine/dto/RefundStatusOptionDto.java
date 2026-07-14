/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.pharmacy.returnmedicine.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 退药状态下拉选项（IF-PHA-02-01）。
 */
@Data
/** 退药状态下拉选项。 */
@Schema(description = "退药状态下拉选项")
public class RefundStatusOptionDto {

    @Schema(description = "状态编码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String statusCode;

    @Schema(description = "状态显示名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String statusName;
}
