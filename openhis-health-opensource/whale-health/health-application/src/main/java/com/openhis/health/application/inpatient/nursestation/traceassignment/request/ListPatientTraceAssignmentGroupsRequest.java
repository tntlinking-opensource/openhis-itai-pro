/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.traceassignment.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-IP-TRC-02：加载患者药批汇总待分配行入参。
 */
@Data
@Accessors(chain = true)
@Schema(description = "加载患者药批汇总待分配行入参", requiredProperties = {"encounterId"})
public class ListPatientTraceAssignmentGroupsRequest {

    /** 就诊主键。 */
    @NotNull(message = "就诊 ID 不能为空")
    @Schema(description = "就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;
}
