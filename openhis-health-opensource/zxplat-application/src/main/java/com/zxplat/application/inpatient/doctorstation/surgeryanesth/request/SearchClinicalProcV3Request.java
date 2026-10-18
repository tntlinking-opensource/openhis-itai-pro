/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.doctorstation.surgeryanesth.request;

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

    /** 检索关键字；可空，空则分页浏览 ACTIVE 术式。 */
    @Schema(description = "检索关键字；可空浏览", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String keyword;

    /** 页码，从 1 起；空默认 1。 */
    @Schema(description = "页码")
    private Integer pageNo;

    /** 每页条数；空默认 20。 */
    @Schema(description = "每页条数")
    private Integer pageSize;
}
