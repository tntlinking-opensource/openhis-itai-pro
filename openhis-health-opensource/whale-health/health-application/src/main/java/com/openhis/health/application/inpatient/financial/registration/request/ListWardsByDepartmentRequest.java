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
 * 按科室查询病区列表请求（IF-IP-REG-05）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "按科室查询病区列表请求")
public class ListWardsByDepartmentRequest {

    /** 住院科室 ID（业务字段）。 */
    @Schema(description = "住院科室 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long organizationId;
}
