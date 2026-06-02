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
import com.openhis.health.domain.emergency.emgtriageassessment.model.VitalsInput;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-EMG-11：体征接入与分级建议。
 */
@Data
@Accessors(chain = true)
@Schema(description = "急诊分诊-体征与建议请求")
public class IngestVitalsAndSuggestLevelRequest {

    @NotNull
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    @NotNull
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "患者标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long patientId;

    @Schema(description = "体征原始入参")
    private VitalsInput vitalsInput;

    @Schema(description = "患者年龄（岁）")
    private Integer patientAgeYears;

    @Schema(description = "机构关闭 MEWS 时 true")
    private Boolean mewsDisabledByOrganization;

    @Schema(description = "AVPU 编码")
    private String avpuCode;

    @Schema(description = "策略追溯键（回显）")
    private String vitalsAutoTierPolicyId;
}
