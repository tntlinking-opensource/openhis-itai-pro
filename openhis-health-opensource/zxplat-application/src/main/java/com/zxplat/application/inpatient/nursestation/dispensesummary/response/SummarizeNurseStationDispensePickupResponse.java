/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.dispensesummary.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 提交汇总领药响应（护士站勾选候选行后生成汇总单）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "提交汇总领药响应")
public class SummarizeNurseStationDispensePickupResponse {

    /** 汇总单号。 */
    @Schema(description = "汇总单号")
    private String summaryNo;
}
