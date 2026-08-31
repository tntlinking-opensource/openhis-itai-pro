/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.keymedicationusage.dto;

import com.openhis.health.domain.share.constants.ChargeItemAmountScaleConstants;
import com.openhis.whale.core.application.annotation.Excel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 重点药品使用统计汇总行（IF-RPT-KDU-02/03）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "重点药品使用统计汇总行")
public class KeyMedicationUsageSummaryRowDto {

    /**
     * 就诊类型码（{@code AMB}/{@code IMP}）。
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
     * 重点药类型码（{@link com.openhis.health.domain.share.enums.KeyDrugType}）。
     */
    @Schema(description = "重点药类型码")
    private String keyDrugTypeCode;

    /**
     * 重点药类型展示名。
     */
    @Excel(name = "药品类型", sort = 2)
    @Schema(description = "重点药类型展示名")
    private String keyDrugTypeDisplay;

    /**
     * 净人次。
     */
    @Excel(name = "人次", sort = 3)
    @Schema(description = "净人次")
    private Long encounterNetCount;

    /**
     * 售价净额合计（展示 2 位）。
     */
    @Excel(name = "总金额", sort = 4, scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_DISPLAY_SCALE)
    @Schema(description = "售价净额合计")
    private BigDecimal saleAmountTotal;
}
