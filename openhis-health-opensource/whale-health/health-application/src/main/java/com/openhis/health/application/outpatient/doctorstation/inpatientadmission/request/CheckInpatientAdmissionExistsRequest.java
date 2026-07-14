/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.inpatientadmission.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-IP-REQ-02：按门诊就诊查询是否已存在住院申请。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊医生站-住院申请-查询是否已存在住院申请请求")
public class CheckInpatientAdmissionExistsRequest {

    /** 源门诊就诊主键。 */
    @NotNull(message = "门诊就诊标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "源门诊就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long ambEncounterId;
}
