/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.pharmacy.patientmedicationusage.response;

import com.openhis.health.application.inpatient.pharmacy.patientmedicationusage.dto.InpatientPatientMedicationUsagePresentationOptionDto;
import com.openhis.health.application.inpatient.pharmacy.patientmedicationusage.dto.InpatientPatientMedicationUsageStatusOptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;
import java.util.List;

/**
 * IF-IP-PMU-01：住院患者药品使用页初始化响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院患者药品使用页初始化响应")
public class LoadInpatientPatientMedicationUsagePageInitResponse {

    /**
     * 默认计划发药日起（服务端当日）。
     */
    @Schema(description = "默认日期起")
    private LocalDate dateFrom;

    /**
     * 默认计划发药日止（服务端当日）。
     */
    @Schema(description = "默认日期止")
    private LocalDate dateTo;

    /**
     * 展示模式可选值（明细 / 汇总）。
     */
    @Schema(description = "展示模式选项")
    private List<InpatientPatientMedicationUsagePresentationOptionDto> presentationOptions;

    /**
     * 展示发药状态三态说明（待发 / 已发 / 已退）。
     */
    @Schema(description = "展示发药状态选项")
    private List<InpatientPatientMedicationUsageStatusOptionDto> usageStatusOptions;
}
