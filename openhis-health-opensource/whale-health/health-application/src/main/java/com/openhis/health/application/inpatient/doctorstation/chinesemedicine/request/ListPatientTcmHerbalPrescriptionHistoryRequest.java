/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.chinesemedicine.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-IP-TCM-39：查询患者历史中药处方请求。
 */
@Data
@Accessors(chain = true)
@Schema(description = "查询患者历史中药处方请求")
public class ListPatientTcmHerbalPrescriptionHistoryRequest {

    /** 患者标识。 */
    @NotNull(message = "患者标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "患者标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long patientId;

    /** 当前就诊标识。 */
    @NotNull(message = "currentEncounterId 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "当前就诊标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long currentEncounterId;

    /** 关键字。 */
    @Schema(description = "关键字")
    private String keyword;
}
