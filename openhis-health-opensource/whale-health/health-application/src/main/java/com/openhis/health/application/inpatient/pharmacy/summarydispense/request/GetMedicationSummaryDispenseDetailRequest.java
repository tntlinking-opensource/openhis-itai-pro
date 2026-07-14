/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.pharmacy.summarydispense.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 查询汇总单详情请求（IF-IPS-02）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "查询汇总单详情 IF-IPS-02", requiredProperties = {"summaryNo"})
public class GetMedicationSummaryDispenseDetailRequest {

    /** 汇总单号 / busNo。 */
    @NotBlank(message = "汇总单号不能为空")
    @Schema(description = "汇总单号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String summaryNo;
}
