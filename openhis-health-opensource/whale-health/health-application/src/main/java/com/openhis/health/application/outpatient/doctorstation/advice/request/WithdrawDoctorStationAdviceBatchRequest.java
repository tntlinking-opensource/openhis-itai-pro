/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.advice.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.outpatient.doctorstation.advice.dto.DoctorStationAdviceResourceKeyDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-DOR-16：签退医嘱批次请求。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站-医嘱开立-签退医嘱批次请求")
public class WithdrawDoctorStationAdviceBatchRequest {

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

    /** 待签退资源键（已签发态方可签退）。 */
    @NotEmpty(message = "签退目标不能为空")
    @Valid
    /** 签退目标列表。 */
    @Schema(description = "签退目标列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<DoctorStationAdviceResourceKeyDto> targets;
}
