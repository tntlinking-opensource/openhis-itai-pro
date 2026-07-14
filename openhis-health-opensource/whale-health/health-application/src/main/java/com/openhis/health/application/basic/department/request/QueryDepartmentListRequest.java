/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.department.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 科室管理列表查询请求（POST /department/getDepartmentList）。
 */
@Data
@Schema(description = "科室管理列表查询请求")
public class QueryDepartmentListRequest {

    @Schema(description = "关键字")
    /** 机构层级类型。 */
    private String keyword;

    @Schema(description = "机构层级类型")
    private String typeEnum;

    /** 机构业务分类。 */
    @Schema(description = "机构业务分类")
    private String classEnum;

    /** 发布状态。 */
    @Schema(description = "发布状态")
    private String statusEnum;
}
