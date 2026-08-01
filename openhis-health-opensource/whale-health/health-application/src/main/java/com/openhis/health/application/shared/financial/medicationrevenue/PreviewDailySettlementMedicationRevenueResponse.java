/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.financial.medicationrevenue;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 日结药品收入 TAB 预览响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "日结药品收入预览响应")
public class PreviewDailySettlementMedicationRevenueResponse {

    @Schema(description = "发药口径药品收入汇总")
    private DailySettlementMedicationRevenueSummaryDto medicationRevenueSummary;

    @Schema(description = "收费侧药品明细")
    private List<DailySettlementMedicationChargeLineDto> chargeDrugLines;

    @Schema(description = "已收费未发药提醒")
    private DailySettlementMedicationUndispensedAlertDto undispensedAlert;
}
