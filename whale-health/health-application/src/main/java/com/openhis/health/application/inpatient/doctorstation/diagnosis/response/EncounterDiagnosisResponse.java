/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.inpatient.doctorstation.diagnosis.response;

import com.openhis.health.application.inpatient.doctorstation.diagnosis.dto.EncounterDiagnosisRowDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-IP-DOR-02：当前住院就诊诊断列表响应（AS-IP-DOR-002）。
 *
 * <p>数据来自住院信息读模型 {@code diagnosisJson} 快照解析，非实时扫表；{@code totalCount} 与列表长度一致。</p>
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
