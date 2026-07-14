/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.diagnosis.response;

import com.openhis.health.application.inpatient.doctorstation.diagnosis.dto.EncounterDiagnosisRowDto;
import com.openhis.health.application.shared.diagnosis.EncounterDiagnosisSnapshotAssembler;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-IP-DOR-02：当前住院就诊诊断列表响应（AS-IP-DOR-002）。
 *
 * <p>数据来自 {@link EncounterDiagnosisSnapshotAssembler} 实时扫表装配后经 Mapper 解析，非读模型 JSON 透传；{@code totalCount} 与列表长度一致。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站-就诊诊断列表响应")
public class EncounterDiagnosisResponse {

    /**
     * 就诊诊断行列表：永不为 null，空就诊返回空列表。
     */
    @Schema(description = "就诊诊断行列表")
    private List<EncounterDiagnosisRowDto> diagnosisList;

    /**
     * 列表总行数：与 {@link #diagnosisList} 的 {@code size()} 一致，供前端分页/角标展示。
     */
    @Schema(description = "列表总数")
    private Integer totalCount;
}
