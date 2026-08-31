/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.financial.settle.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 住院平台在院对齐（诊断/修复）请求。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院平台在院对齐请求", requiredProperties = {"encounterId", "patientId", "contractId", "psnNo"})
public class AlignInpatientPlatformVisitRequest {

    @NotNull
    @Schema(description = "住院就诊 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    @NotNull
    @Schema(description = "患者 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;

    @NotNull
    @Schema(description = "医保合同 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long contractId;

    @NotNull
    @Schema(description = "人员编号 psn_no", requiredMode = Schema.RequiredMode.REQUIRED)
    private String psnNo;

    @Schema(description = "参保地医保区划")
    private String insuplcAdmdvs;
}
