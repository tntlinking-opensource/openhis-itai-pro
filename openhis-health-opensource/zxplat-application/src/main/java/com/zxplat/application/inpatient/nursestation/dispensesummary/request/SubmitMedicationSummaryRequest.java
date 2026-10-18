/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.dispensesummary.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 发送汇总领药单请求（IF-IP-SUM-05）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "发送汇总领药单 IF-IP-SUM-05", requiredProperties = {"summaryNos"})
public class SubmitMedicationSummaryRequest {

    /** 汇总单号列表。 */
    @NotEmpty(message = "请至少选择一张汇总单")
    @Schema(description = "汇总单号列表", required = true)
    private List<String> summaryNos;
}
