/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.doctorstation.chinesemedicine.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

/**
 * IF-IP-TCM-40：停嘱（长期中药方）请求。
 */
@Data
@Accessors(chain = true)
@Schema(description = "停嘱长期中药方请求")
public class StopLongTermTcmHerbalPrescriptionRequest {

    /** 就诊标识。 */
    @NotNull(message = "就诊标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /** 患者标识。 */
    @NotNull(message = "患者标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "患者标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long patientId;

    /** 待停嘱处方号列表。 */
    @NotEmpty(message = "prescriptionNos 不能为空")
    @Schema(description = "待停嘱处方号列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<String> prescriptionNos;

    /** 停嘱时间。 */
    @Schema(description = "停嘱时间")
    private LocalDateTime stopTime;
}
