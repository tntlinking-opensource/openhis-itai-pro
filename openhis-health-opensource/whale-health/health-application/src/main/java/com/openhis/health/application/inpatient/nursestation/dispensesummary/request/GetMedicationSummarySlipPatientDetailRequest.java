/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.dispensesummary.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 查询汇总单摆药明细（按患者 + medReqId）请求（IF-IP-SUM-09）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "查询汇总单摆药明细 IF-IP-SUM-09", requiredProperties = {"summaryNo"})
public class GetMedicationSummarySlipPatientDetailRequest {

    /** 汇总单号（与发放行 summary_no / Supply bus_no 一致）。 */
    @NotBlank(message = "汇总单号不能为空")
    @Schema(description = "汇总单号", required = true)
    private String summaryNo;
}
