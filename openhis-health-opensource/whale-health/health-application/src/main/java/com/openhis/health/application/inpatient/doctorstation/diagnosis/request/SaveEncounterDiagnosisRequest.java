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

package com.openhis.health.application.inpatient.doctorstation.diagnosis.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-IP-DOR-05：医生保存住院就诊诊断（全量批次）请求（AS-IP-DOR-002）。
 *
 * <p>采用全量覆盖语义：先批量保存诊断事实，再批量 upsert 就诊诊断关联；子项顺序与领域返回 id 顺序须一致。</p>
 */
@Data
@Accessors(chain = true)
/** 医生站-保存就诊诊断请求。 */
@Schema(description = "医生站-保存就诊诊断请求")
public class SaveEncounterDiagnosisRequest {

    /**
     * 住院就诊主键：写入就诊诊断关联的锚点。
     */
    @NotNull(message = "就诊标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /**
     * 患者主键：与就诊、读模型快照交叉校验。
     */
    @NotNull(message = "患者标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "患者标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long patientId;

    /**
     * 诊断全量批次：含新增与更新行，至少一条；主诊唯一与排序唯一由 Mapper Gate 校验。
     */
    @NotEmpty(message = "诊断批次不能为空")
    @Valid
    @Schema(description = "诊断批次列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<SaveEncounterDiagnosisChildRequest> diagnosisChildList;
}
