/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.ATDManager.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-IP-NUR-ATD-03：出院前待执行医嘱列表入参。
 */
@Data
@Accessors(chain = true)
@Schema(description = "出院前待执行医嘱列表请求", requiredProperties = {"encounterId"})
public class ListDischargePendingExecutionsRequest {

    /** 就诊主键。 */
    @NotNull(message = "就诊标识不能为空")
    @Schema(description = "就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;
}
