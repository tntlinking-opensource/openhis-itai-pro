/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.pharmacy.patientmedicationusage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 展示发药状态选项（待发 / 已发 / 已退）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院患者药品使用-展示发药状态选项")
public class InpatientPatientMedicationUsageStatusOptionDto {

    /**
     * 展示状态 code：{@code PENDING} / {@code DISPENSED} / {@code RETURNED}。
     */
    @Schema(description = "展示发药状态 code")
    private String code;

    /**
     * 展示文案：待发 / 已发 / 已退。
     */
    @Schema(description = "展示文案")
    private String display;
}
