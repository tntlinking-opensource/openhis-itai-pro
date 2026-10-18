/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.keymedicationusage.request;

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
     * 重点药类型多选（{@code BASIC}/{@code NATIONAL_MONITOR}/{@code CENTRAL_PURCHASE}/{@code ANTIBIOTIC}）；
     * 空/null 且药品性质亦空 → 七类全开；仅本框有值 → 只出四旗行。
     */
    @Schema(description = "重点药类型多选（四旗）")
    private List<String> keyDrugTypeCodes;

    /**
     * 药品性质多选（{@code NARCOTIC}/{@code PSYCH_ONE}/{@code PSYCH_TWO}）；与重点药类型分框；
     * 空/null 且重点药亦空 → 七类全开；仅本框有值 → 只出麻/精行。
     */
    @Schema(description = "药品性质多选（麻/精一/精二）")
    private List<String> medicinePropertyCodes;
}
