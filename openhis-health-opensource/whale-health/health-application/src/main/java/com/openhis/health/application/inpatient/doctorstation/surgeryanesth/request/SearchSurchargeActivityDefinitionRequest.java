/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.surgeryanesth.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * AS-IP-DOR-012 请求：SearchSurchargeActivityDefinition。
 */
@Data
@Accessors(chain = true)
@Schema(description = "SearchSurchargeActivityDefinitionRequest")
public class SearchSurchargeActivityDefinitionRequest {

    /** 关键字。 */
    @Schema(description = "关键字", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String keyword;

    /** 页码。 */
    @Schema(description = "页码", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Integer pageNo;

    /** 每页条数。 */
    @Schema(description = "每页条数", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Integer pageSize;

    /** 侧别 SURGERY/ANESTHESIA。 */
    @Schema(description = "侧别 SURGERY/ANESTHESIA", requiredMode = Schema.RequiredMode.REQUIRED)
    private String sideCode;
}
