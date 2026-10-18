/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.doctorstation.diagnosis.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-IP-DOR-02：查询当前住院就诊诊断列表请求（AS-IP-DOR-002）。
 *
 * <p>患者主键须与 {@code InpatientInfoReadModel} 快照一致，由应用服务与 Mapper Gate 交叉校验，防止串患者上下文。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站-查询就诊诊断信息请求")
public class GetEncounterDiagnosisRequest {

    /**
     * 住院就诊主键：定位读模型 {@code diagnosisJson} 快照行。
     */
    @NotNull(message = "就诊标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /**
     * 患者主键：须与读模型 {@code patientId} 一致，用于患者卡片上下文门控。
     */
    @NotNull(message = "患者标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "患者标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long patientId;
}
