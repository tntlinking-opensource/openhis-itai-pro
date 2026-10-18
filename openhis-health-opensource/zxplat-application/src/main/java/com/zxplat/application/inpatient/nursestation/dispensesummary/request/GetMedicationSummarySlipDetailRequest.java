/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.dispensesummary.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 查询汇总单合并明细请求（IF-IP-SUM-03）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "查询汇总单合并明细 IF-IP-SUM-03", requiredProperties = {"summaryNo"})
public class GetMedicationSummarySlipDetailRequest {

    /** 汇总单号。 */
    @NotBlank(message = "汇总单号不能为空")
    @Schema(description = "汇总单号", required = true)
    private String summaryNo;
}
