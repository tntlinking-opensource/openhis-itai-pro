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

package com.openhis.health.application.emergency.emgtriage.station.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-EMG-21：导出或删除分诊数据。
 */
@Data
@Accessors(chain = true)
@Schema(description = "急诊分诊-导出或删除请求")
public class ExportOrDeleteEmgTriageDataRequest {

    @NotBlank
    @Schema(description = "EXPORT 导出占位；VOID 作废删除", requiredMode = Schema.RequiredMode.REQUIRED)
    private String action;

    @NotNull
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "目标分诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long emgTriageId;

    @Schema(description = "作废原因（VOID 时必填）")
    private String voidReason;
}
