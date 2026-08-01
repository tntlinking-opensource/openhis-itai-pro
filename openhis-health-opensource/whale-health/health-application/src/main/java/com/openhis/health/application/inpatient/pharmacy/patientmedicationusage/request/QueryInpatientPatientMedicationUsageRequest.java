/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.pharmacy.patientmedicationusage.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;

/**
 * IF-IP-PMU-03：查询住院患者药品使用请求。
 *
 * <p>单就诊 + 计划发药日闭区间 + 明细/汇总展示模式（BR-IP-PMU-001/002/011）。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院患者药品使用查询请求",
    requiredProperties = {"encounterId", "patientId", "dateFrom", "dateTo", "presentationMode"})
public class QueryInpatientPatientMedicationUsageRequest {

    /**
     * 当前选中就诊主键；须与 patientId 交叉一致。
     */
    @NotNull(message = "encounterId 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /**
     * 患者主键；须与候选行 / 就诊归属一致。
     */
    @NotNull(message = "patientId 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "患者主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long patientId;

    /**
     * 计划发药日下界（闭区间，本地日历日）。
     */
    @NotNull(message = "dateFrom 不能为空")
    @Schema(description = "计划发药日起（闭）", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate dateFrom;

    /**
     * 计划发药日上界（闭区间）；须 {@code dateFrom ≤ dateTo}。
     */
    @NotNull(message = "dateTo 不能为空")
    @Schema(description = "计划发药日止（闭）", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate dateTo;

    /**
     * 展示模式：{@code DETAIL} 明细 / {@code SUMMARY} 汇总。
     */
    @NotBlank(message = "presentationMode 不能为空")
    @Schema(description = "展示模式 DETAIL|SUMMARY", requiredMode = Schema.RequiredMode.REQUIRED)
    private String presentationMode;
}
