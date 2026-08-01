/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.pharmacy.patientmedicationusage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 展示模式选项（明细 / 汇总）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院患者药品使用-展示模式选项")
public class InpatientPatientMedicationUsagePresentationOptionDto {

    /**
     * 展示模式 code：{@code DETAIL} / {@code SUMMARY}。
     */
    @Schema(description = "展示模式 code")
    private String code;

    /**
     * 展示文案：明细 / 汇总。
     */
    @Schema(description = "展示文案")
    private String display;
}
