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
/** 医生站-删除就诊诊断响应。 */
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
