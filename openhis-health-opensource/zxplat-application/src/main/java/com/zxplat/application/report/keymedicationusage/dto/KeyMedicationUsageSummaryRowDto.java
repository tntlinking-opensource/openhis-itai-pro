/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.keymedicationusage.dto;

import com.zxplat.domain.share.constants.ChargeItemAmountScaleConstants;
import com.zxplat.domain.share.constants.KeyMedicationUsageReportConstants;
import com.zxplat.framework.core.application.annotation.Excel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 重点药品使用统计汇总行（IF-RPT-KDU-02/03）。
 *
 * <p>一行 = 某就诊类型下某一重点药类型，或该就诊类型分组末的「合计」行
 * （{@code keyDrugTypeCode=TOTAL}）。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "重点药品使用统计汇总行")
public class KeyMedicationUsageSummaryRowDto {

    /**
     * 就诊类型码（{@code AMB}/{@code IMP}；分组合计行仍为本组就诊类型，不用 TOTAL）。
     */
    @Schema(description = "就诊类型码")
    private String encounterClassCode;

    /**
     * 就诊类型展示名（门诊/住院）。
     */
    @Excel(name = "就诊类型", sort = 1)
    @Schema(description = "就诊类型展示名")
    private String encounterClassDisplay;

    /**
     * 重点药类型码（{@code BASIC}/{@code NATIONAL_MONITOR}/{@code CENTRAL_PURCHASE}/{@code ANTIBIOTIC}），
     * 或分组合计 {@link KeyMedicationUsageReportConstants#SUMMARY_KEY_DRUG_TYPE_TOTAL}。
     */
    @Schema(description = "重点药类型码")
    private String keyDrugTypeCode;

    /**
     * 重点药类型展示名（基药/国监/集采/抗生素/合计）。
     */
    @Excel(name = "药品类型", sort = 2)
    @Schema(description = "重点药类型展示名")
    private String keyDrugTypeDisplay;

    /**
     * 本行净人次（类型行=该类型；合计行=所选类型<strong>并集</strong>去重人次）。
     */
    @Excel(name = "人次", sort = 3)
    @Schema(description = "净人次")
    private Long encounterNetCount;

    /**
     * 本行售价净额合计（展示 2 位；合计行=所选类型并集金额，多旗药品行只计一次）。
     */
    @Excel(name = "总金额", sort = 4, scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_DISPLAY_SCALE)
    @Schema(description = "售价净额合计")
    private BigDecimal saleAmountTotal;

    /**
     * 人次占比百分比（例 20.00 表示 20%）：类型行 = 该类型净人次 / <strong>并集合计人次</strong> × 100；
     * 合计行为 100%；分母为 0 时为 null。多旗重叠时各类型占比之和可大于 100%。
     */
    @Excel(name = "人次占比(%)", sort = 5, scale = KeyMedicationUsageReportConstants.USAGE_RATIO_PERCENT_SCALE)
    @Schema(description = "人次占比百分比")
    private BigDecimal usageRatioPercent;

    /**
     * 金额占比百分比：类型行 = 该类型金额 / <strong>并集合计金额</strong> × 100；
     * 合计行为 100%；分母为 0 时为 null。
     */
    @Excel(name = "金额占比(%)", sort = 6, scale = KeyMedicationUsageReportConstants.USAGE_RATIO_PERCENT_SCALE)
    @Schema(description = "金额占比百分比")
    private BigDecimal saleAmountRatioPercent;
}

