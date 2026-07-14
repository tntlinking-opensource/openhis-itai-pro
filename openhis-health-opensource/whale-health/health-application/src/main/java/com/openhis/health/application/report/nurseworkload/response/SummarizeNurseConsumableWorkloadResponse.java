/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.nurseworkload.response;

import com.openhis.health.application.report.nurseworkload.dto.NurseConsumableSummaryRowDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * 护士耗材使用汇总查询出参（IF-RPT-NWL-04）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "护士耗材使用汇总 IF-RPT-NWL-04")
public class SummarizeNurseConsumableWorkloadResponse {

    /** 汇总行。 */
    @Schema(description = "汇总行")
    private List<NurseConsumableSummaryRowDto> rows;

    /** 合计数量。 */
    @Schema(description = "合计数量")
    private BigDecimal totalQuantity;

    /** 合计金额。 */
    @Schema(description = "合计金额")
    private BigDecimal totalAmount;
}
