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

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-EMG-15：分诊挂号协同（与 {@link SaveOrSubmitEmgTriageRequest} 载荷同构编排）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "急诊分诊-分诊挂号协同请求")
public class CoordinateTriageRegistrationRequest {

    @NotBlank
    @Schema(description = "协同模式 TRIAGE_FIRST / REGISTER_FIRST / CONCURRENT", requiredMode = Schema.RequiredMode.REQUIRED)
    private String triageRegistrationModeEnum;

    @NotNull
    @Valid
    @Schema(description = "分诊保存载荷（与 IF-EMG-06 一致）", requiredMode = Schema.RequiredMode.REQUIRED)
    private SaveOrSubmitEmgTriageRequest triage;
}
