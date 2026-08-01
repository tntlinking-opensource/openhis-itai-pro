/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.medicationdispense.outpatient.response;

import com.openhis.health.application.report.medicationdispense.outpatient.dto.OutpatientMedicationDispenseSummaryRowDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * 门诊发药报表汇总响应（IF-RPT-OMD-02）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊发药报表汇总响应")
public class SummarizeOutpatientMedicationDispenseReportResponse {

    /** 汇总行。 */
    @Schema(description = "汇总行列表")
    private List<OutpatientMedicationDispenseSummaryRowDto> rows;

    /** 页脚净数量合计。 */
    @Schema(description = "净数量合计")
    private BigDecimal totalQuantity;

    /** 页脚进价净额合计；无可价行时可为 null。 */
    @Schema(description = "进价净额合计")
    private BigDecimal purchaseTotalAmount;

    /** 页脚售价净额合计；无可价行时可为 null。 */
    @Schema(description = "售价净额合计")
    private BigDecimal saleTotalAmount;

    /** 页脚净金额合计（售价别名；无可价行时可为 null）。 */
    @Schema(description = "净金额合计")
    private BigDecimal totalAmount;
}
