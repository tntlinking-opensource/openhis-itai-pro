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
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-EMG-04：多源身份采集。
 */
@Data
@Accessors(chain = true)
@Schema(description = "急诊分诊-身份采集请求")
public class CaptureIdentityForEmergencyTriageRequest {

    @NotBlank
    @Schema(description = "渠道编码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String channelCode;

    @NotBlank
    @Schema(description = "设备回传原文", requiredMode = Schema.RequiredMode.REQUIRED)
    private String rawPayload;
}
