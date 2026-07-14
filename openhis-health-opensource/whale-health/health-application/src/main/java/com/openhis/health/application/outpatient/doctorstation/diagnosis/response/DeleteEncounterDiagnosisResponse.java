/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.diagnosis.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-DOR-09：删除就诊诊断响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站-删除就诊诊断响应")
public class DeleteEncounterDiagnosisResponse {

    @Schema(description = "删除是否成功")
    /** 操作结果提示。 */
    private Boolean success;

    @Schema(description = "操作结果提示")
    private String message;
}
