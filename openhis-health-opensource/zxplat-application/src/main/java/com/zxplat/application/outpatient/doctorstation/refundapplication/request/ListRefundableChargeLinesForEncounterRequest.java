/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.doctorstation.refundapplication.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-DRA-01：按就诊加载可申请退费账单行。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊医生站-申请退费-加载可申请退费账单行请求")
public class ListRefundableChargeLinesForEncounterRequest {

    /** 当前就诊主键（医生站上下文）。 */
    @NotNull(message = "就诊标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "当前就诊主键（医生站上下文）", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;
}
