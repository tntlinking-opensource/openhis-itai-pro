/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.financial.medicationrevenue;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 日结药品 TAB：发药口径收入汇总（按 fin_type）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "日结药品收入汇总（发药口径）")
public class DailySettlementMedicationRevenueSummaryDto {

    @Schema(description = "发药口径药品收入合计")
    private BigDecimal totalMedicationIncome;

    @Schema(description = "按财务类别拆分的发药口径收入")
    private List<DailySettlementMedicationFinTypeAmountDto> finTypeAmounts = new ArrayList<>();
}
