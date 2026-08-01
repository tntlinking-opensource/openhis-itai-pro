/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.pharmacy.patientmedicationusage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 住院患者药品使用合计区（两种展示模式均返回净额）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院患者药品使用-合计")
public class InpatientPatientMedicationUsageTotalsDto {

    /**
     * 药品使用净金额合计（明细/汇总口径一致，BR-IP-PMU-008）。
     */
    @Schema(description = "净金额合计")
    private BigDecimal grandTotal;

    /**
     * 当前模式下行数（明细行数或汇总行数）。
     */
    @Schema(description = "当前模式行数")
    private Integer lineCount;
}
