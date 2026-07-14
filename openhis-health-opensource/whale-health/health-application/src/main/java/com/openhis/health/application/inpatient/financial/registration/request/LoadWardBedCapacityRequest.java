/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.financial.registration.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 病区床位余量查询请求（IF-IP-REG-05）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "病区床位余量查询请求")
public class LoadWardBedCapacityRequest {

    /** 病区位置 ID（业务字段）。 */
    @Schema(description = "病区位置 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long wardId;
}
