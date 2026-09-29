/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.medicaltech.charging.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.medicaltech.charging.dto.MedicalTechDevicePricingCancelItemDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 取消医技耗材划价请求（AS-MT-CHG-001 IF-MT-CHG-04）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "取消医技耗材划价请求")
public class CancelMedicalTechDevicePricingRequest {

    /** 住院就诊主键。 */
    @Schema(description = "就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 取消行（至少一行；requestId 去重）。 */
    @Schema(description = "取消行", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<MedicalTechDevicePricingCancelItemDto> items;
}
