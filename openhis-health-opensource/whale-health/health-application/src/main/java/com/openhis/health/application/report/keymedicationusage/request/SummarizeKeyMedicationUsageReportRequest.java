/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.keymedicationusage.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 重点药品使用统计汇总请求（IF-RPT-KDU-02）。
 */
@Data
@Schema(description = "重点药品使用统计汇总请求")
public class SummarizeKeyMedicationUsageReportRequest {

    /**
     * 业务日起（必填，{@code yyyy-MM-dd}）。
     */
    @Schema(description = "业务日起")
    private String dateFrom;

    /**
     * 业务日止（必填，{@code yyyy-MM-dd}）。
     */
    @Schema(description = "业务日止")
    private String dateTo;

    /**
     * 就诊类型多选（{@code AMB}/{@code IMP}）；空/null 表示全部。
     */
    @Schema(description = "就诊类型多选")
    private List<String> encounterClassCodes;

    /**
     * 重点药类型多选（{@code BASIC}/{@code NATIONAL_MONITOR}/{@code CENTRAL_PURCHASE}）；空/null 表示全部。
     */
    @Schema(description = "重点药类型多选")
    private List<String> keyDrugTypeCodes;
}
