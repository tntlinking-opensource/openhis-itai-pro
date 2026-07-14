/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.advice.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.outpatient.doctorstation.advice.dto.DoctorStationAdviceDraftLineRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-DOR-14：暂存医嘱变更批次请求。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站-医嘱开立-暂存医嘱变更批次请求")
public class SaveDoctorStationAdviceDraftBatchRequest {

    /** 就诊标识。 */
    @NotNull(message = "就诊标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /** 必须与就诊患者一致，由 AppService 与领域二次校验。 */
    @NotNull(message = "患者标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 患者标识。 */
    @Schema(description = "患者标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long patientId;

    /** 本批次 CREATE/UPDATE/DELETE 行集合，顺序即处理建议顺序。 */
    @NotEmpty(message = "批次行不能为空")
    @Valid
    /** 批次行。 */
    @Schema(description = "批次行", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<DoctorStationAdviceDraftLineRequest> lines;
}
