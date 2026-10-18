/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.dispensesummary.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 汇总保存响应（IF-IP-SUM-04）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "生成汇总领药单响应")
public class CreateMedicationSummaryResponse {

    @Schema(description = "汇总单号列表")
    /** 汇总单号列表。 */
    private List<String> summaryNos;

    /** 成功提示。 */
    @Schema(description = "成功提示")
    private String message;
}
