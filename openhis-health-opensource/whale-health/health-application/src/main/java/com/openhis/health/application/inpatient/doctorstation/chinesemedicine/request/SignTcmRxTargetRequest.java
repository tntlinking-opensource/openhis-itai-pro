/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.chinesemedicine.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-DOR-37：签发中药处方目标行。
 */
@Data
@Accessors(chain = true)
@Schema(description = "签发中药处方目标行")
public class SignTcmRxTargetRequest {

    /** 前端方标识。 */
    @NotBlank(message = "prescriptionGroupKey 不能为空")
    @Schema(description = "前端方标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private String prescriptionGroupKey;

    /** 药品申请主键。 */
    @NotNull(message = "药品申请主键不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "药品申请主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long medicationRequestId;
}
