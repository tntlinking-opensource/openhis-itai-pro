/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.registration.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 科室列表请求。
 */
@Data
@Accessors(chain = true)
@Schema(description = "科室列表请求")
public class ListDepartmentsRequest {

    /** 关键字（业务字段）。 */
    @Schema(description = "关键字")
    private String keyword;
}
