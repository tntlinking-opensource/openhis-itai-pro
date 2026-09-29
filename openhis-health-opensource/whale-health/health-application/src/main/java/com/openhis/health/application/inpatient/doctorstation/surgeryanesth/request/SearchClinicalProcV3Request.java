/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.surgeryanesth.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * AS-IP-DOR-012 请求：SearchClinicalProcV3。
 */
@Data
@Accessors(chain = true)
@Schema(description = "SearchClinicalProcV3Request")
public class SearchClinicalProcV3Request {

    /** 检索关键字。 */
    @Schema(description = "检索关键字", requiredMode = Schema.RequiredMode.REQUIRED)
    private String keyword;
}
