/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.diagnosis.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-IP-DOR-06：删除住院就诊诊断响应（AS-IP-DOR-002）。
 *
 * <p>幂等删除成功时 {@code success=true}；{@code message} 为面向用户的操作结果提示。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站-删除就诊诊断响应")
public class DeleteEncounterDiagnosisResponse {

    /**
     * 是否视为删除成功（含幂等：关联已不存在且事实已删）。
     */
    @Schema(description = "删除是否成功")
    private Boolean success;

    /**
     * 操作结果提示文案。
     */
    @Schema(description = "操作结果提示")
    private String message;
}
