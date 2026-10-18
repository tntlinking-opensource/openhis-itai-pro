/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.doctorstation.diagnosis.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-IP-DOR-05：保存住院就诊诊断响应（AS-IP-DOR-002）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站-保存就诊诊断响应")
public class SaveEncounterDiagnosisResponse {

    @Schema(description = "保存成功条数")
    private Integer savedCount;

    @Schema(description = "保存后的就诊诊断行主键列表")
    private List<Long> savedEncounterDiagnosisIds;
}
