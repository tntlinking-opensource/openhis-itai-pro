/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.medicaltech.charging.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 取消医技耗材划价行键（AS-MT-CHG-001 IF-MT-CHG-04）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "取消医技耗材划价行")
public class MedicalTechDevicePricingCancelItemDto {

    /** DeviceRequest 主键。 */
    @Schema(description = "耗材请求主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long requestId;
}
