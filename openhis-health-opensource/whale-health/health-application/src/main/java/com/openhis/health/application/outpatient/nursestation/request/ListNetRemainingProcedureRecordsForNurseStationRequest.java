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

package com.openhis.health.application.outpatient.nursestation.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 查询净剩可取消执行记录列表请求（IF-NUR-03B）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "净剩可取消执行记录列表请求")
public class ListNetRemainingProcedureRecordsForNurseStationRequest {

    /**
     * 就诊主键：列表必须限定在同一就诊。
     */
    @NotNull(message = "encounterId 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /**
     * 可选：缩窄至单条来源请求。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "来源请求主键（与 requestTable 成对使用）")
    private Long requestId;

    /**
     * 可选：与 requestId 成对传入的来源表持久化名。
     */
    @Schema(description = "来源表持久化名（与 requestId 成对使用）")
    private String requestTable;
}
