/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.executelocation.request;

import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 按执行科室分页查询已绑定的执行规则列表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class QueryExecuteLocationRequest extends AbstractHealthPageRequest {

    /** 执行科室主键，必填。 */
    private Long executionOrgId;
    /** 诊疗大类过滤；可空表示不按大类过滤（由查询服务解释）。 */
    private String activityCategoryCode;
}
