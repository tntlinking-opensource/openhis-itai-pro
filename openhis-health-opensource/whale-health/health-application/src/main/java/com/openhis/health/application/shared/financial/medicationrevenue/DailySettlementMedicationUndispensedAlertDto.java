/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.financial.medicationrevenue;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 日结药品 TAB：已收费未发药提醒。
 */
@Data
@Accessors(chain = true)
@Schema(description = "已收费未发药提醒")
public class DailySettlementMedicationUndispensedAlertDto {

    @Schema(description = "未发药笔数")
    private Integer count;

    @Schema(description = "未发药明细（可截断）")
    private List<DailySettlementMedicationChargeLineDto> items = new ArrayList<>();
}
