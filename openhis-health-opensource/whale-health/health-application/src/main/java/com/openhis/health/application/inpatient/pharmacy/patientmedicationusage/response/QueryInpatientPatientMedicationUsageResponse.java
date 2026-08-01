/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.pharmacy.patientmedicationusage.response;

import com.openhis.health.application.inpatient.pharmacy.patientmedicationusage.dto.InpatientPatientMedicationUsageDetailLineDto;
import com.openhis.health.application.inpatient.pharmacy.patientmedicationusage.dto.InpatientPatientMedicationUsageSummaryLineDto;
import com.openhis.health.application.inpatient.pharmacy.patientmedicationusage.dto.InpatientPatientMedicationUsageTotalsDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-IP-PMU-03：查询住院患者药品使用响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院患者药品使用查询响应")
public class QueryInpatientPatientMedicationUsageResponse {

    /**
     * 展示模式回显：{@code DETAIL} / {@code SUMMARY}。
     */
    @Schema(description = "展示模式回显")
    private String presentationMode;

    /**
     * 明细行；仅 {@code DETAIL} 时填充，否则空列表。
     */
    @Schema(description = "明细行列表")
    private List<InpatientPatientMedicationUsageDetailLineDto> detailLines;

    /**
     * 汇总行；仅 {@code SUMMARY} 时填充，否则空列表。
     */
    @Schema(description = "汇总行列表")
    private List<InpatientPatientMedicationUsageSummaryLineDto> summaryLines;

    /**
     * 合计区；两种模式均返回净额。
     */
    @Schema(description = "合计区")
    private InpatientPatientMedicationUsageTotalsDto totals;
}
