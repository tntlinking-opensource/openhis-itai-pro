/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.financial.medicationrevenue;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 日结药品 TAB：单财务类别发药口径金额。
 */
@Data
@Accessors(chain = true)
@Schema(description = "药品财务类别金额")
public class DailySettlementMedicationFinTypeAmountDto {

    @Schema(description = "财务类别码")
    private String finTypeCode;

    @Schema(description = "财务类别名")
    private String finTypeName;

    @Schema(description = "金额")
    private BigDecimal amount;
}
