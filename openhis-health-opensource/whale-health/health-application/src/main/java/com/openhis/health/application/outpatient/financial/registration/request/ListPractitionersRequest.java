/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.registration.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 医生列表请求。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生列表请求")
public class ListPractitionersRequest {

    /** 科室ID（业务字段）。 */
    @Schema(description = "科室ID")
    private Long organizationId;

    /** 检索关键字。 */
    @Schema(description = "关键字")
    private String keyword;
}
