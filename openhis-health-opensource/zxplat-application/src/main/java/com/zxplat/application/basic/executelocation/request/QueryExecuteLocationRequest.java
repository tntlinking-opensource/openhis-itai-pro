/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.executelocation.request;

import com.zxplat.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 按开立科室分页查询已绑定的执行规则列表（WH-EXE001）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class QueryExecuteLocationRequest extends AbstractHealthPageRequest {

    /** 开立科室主键，必填。 */
    private Long organizationId;
    /** 诊疗大类过滤；可空表示不按大类过滤。 */
    private String activityCategoryCode;
}
