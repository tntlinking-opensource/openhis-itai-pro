/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.advice.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.outpatient.doctorstation.advice.dto.AssignMedicationGroupPayload;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-DOR-17：更新药品医嘱组号请求。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站-医嘱开立-更新药品医嘱组号请求")
public class UpdateDoctorStationMedicationAdviceGroupsRequest {

    /** 就诊标识。 */
    @NotNull(message = "就诊标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /** 患者门控。 */
    @NotNull(message = "患者标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 患者标识。 */
    @Schema(description = "患者标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long patientId;

    /** 需要拆组的药品请求主键列表。 */
    @Schema(description = "需要拆组的药品请求主键列表")
    private List<Long> clearGroupIds;

    /** 并组写入载荷。 */
    @Schema(description = "并组写入载荷")
    private AssignMedicationGroupPayload assignGroup;
}
