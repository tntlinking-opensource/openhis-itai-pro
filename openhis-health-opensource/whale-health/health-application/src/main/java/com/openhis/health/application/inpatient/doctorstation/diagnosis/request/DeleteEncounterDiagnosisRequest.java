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
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-IP-DOR-06：删除住院就诊诊断请求（AS-IP-DOR-002）。
 *
 * <p>先解除就诊诊断关联，再软删诊断事实；写路径须通过 IMP 就诊类别与可写状态 Gate。</p>
 */
@Data
@Accessors(chain = true)
/** 医生站-删除就诊诊断请求。 */
@Schema(description = "医生站-删除就诊诊断请求")
public class DeleteEncounterDiagnosisRequest {

    /**
     * 待删除的诊断事实（Condition）主键。
     */
    @NotNull(message = "诊断标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "诊断事实标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long conditionId;

    /**
     * 当前住院就诊主键：用于解除关联与就诊上下文校验。
     */
    @NotNull(message = "就诊标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /**
     * 患者主键：删除时用于就诊一致性校验；住院删除接口可不传。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "患者标识；删除时用于就诊一致性校验")
    private Long patientId;
}
