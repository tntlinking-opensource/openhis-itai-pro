/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.advice.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-DOR-17：并组写入载荷。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站药品医嘱并组载荷")
public class AssignMedicationGroupPayload {

    /** 并组目标组号，与药品请求 {@code group_id} 对齐。 */
    @NotNull(message = "组号不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "目标组号", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long groupId;

    /** 待并入同组的 {@code med_medication_request} 主键列表。 */
    @NotEmpty(message = "药品请求列表不能为空")
    /** 药品请求主键列表。 */
    @Schema(description = "药品请求主键列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> medicationRequestIds;
}
