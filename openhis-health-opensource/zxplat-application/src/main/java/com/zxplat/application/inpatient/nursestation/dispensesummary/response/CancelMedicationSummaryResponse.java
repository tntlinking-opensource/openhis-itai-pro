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
 * 撤销汇总领药单响应（IF-IP-SUM-08）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "撤销汇总领药单响应")
public class CancelMedicationSummaryResponse {

    @Schema(description = "已撤销汇总单号列表")
    /** 已撤销汇总单号列表。 */
    private List<String> summaryNos;

    /** 成功提示。 */
    @Schema(description = "成功提示")
    private String message;
}
