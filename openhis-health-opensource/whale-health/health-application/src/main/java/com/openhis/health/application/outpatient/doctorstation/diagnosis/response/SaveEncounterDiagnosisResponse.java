/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.diagnosis.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-DOR-08：保存就诊诊断响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站-保存就诊诊断响应")
public class SaveEncounterDiagnosisResponse {

    @Schema(description = "保存成功条数")
    /** 保存后的诊断事实标识列表。 */
    private Integer savedCount;

    @Schema(description = "保存后的诊断事实标识列表")
    private List<Long> savedConditionIds;
}
