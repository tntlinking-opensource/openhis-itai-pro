/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.doctorstation.advice.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.application.inpatient.doctorstation.advice.dto.InpatientDoctorStationAdviceDraftLineRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-IP-DOR-23：住院暂存前绑定解析请求（与 IF-DOR-23 同构，纯读）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院医生站-暂存前绑定解析请求")
public class ResolveInpatientDoctorStationAdviceDraftBindsRequest {

    /** 就诊标识。 */
    @NotNull(message = "就诊标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /** 患者标识。 */
    @NotNull(message = "患者标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "患者标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long patientId;

    /** 暂存变更行。 */
    @NotEmpty(message = "暂存变更行不能为空")
    @Valid
    @Schema(description = "暂存变更行", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<InpatientDoctorStationAdviceDraftLineRequest> lines;
}
