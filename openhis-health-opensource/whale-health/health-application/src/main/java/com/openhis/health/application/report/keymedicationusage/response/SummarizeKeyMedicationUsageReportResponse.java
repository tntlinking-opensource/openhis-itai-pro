/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.keymedicationusage.response;

import com.openhis.health.application.report.keymedicationusage.dto.KeyMedicationUsageSummaryRowDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * 重点药品使用统计汇总响应（IF-RPT-KDU-02）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "重点药品使用统计汇总响应")
public class SummarizeKeyMedicationUsageReportResponse {

    /**
     * 矩阵汇总行（就诊类型 × 重点药类型）。
     */
    @Schema(description = "汇总行")
    private List<KeyMedicationUsageSummaryRowDto> rows;

    /**
     * 净人次合计（各行人次之和；非去重）。
     */
    @Schema(description = "净人次合计")
    private Long totalEncounterNetCount;

    /**
     * 售价净额合计。
     */
    @Schema(description = "售价净额合计")
    private BigDecimal totalSaleAmount;
}
