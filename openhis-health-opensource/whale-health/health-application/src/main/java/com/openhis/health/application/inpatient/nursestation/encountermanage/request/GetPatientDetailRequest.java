/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.encountermanage.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 患者详情查询请求（IF-IP-NUR-03）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "患者详情查询请求", requiredProperties = {"encounterId"})
public class GetPatientDetailRequest {

    /** 住院就诊主键。 */
    @NotNull(message = "就诊标识不能为空")
    @Schema(description = "住院就诊主键", required = true)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;
}
