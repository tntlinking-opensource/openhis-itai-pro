/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.executelocation.request;

import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 为执行地点配置「挑选诊疗定义」提供的分页查询：走产品定义读模型，限定活动定义表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class QueryActivityDefinitionForExecuteLocationRequest extends AbstractHealthPageRequest {

    /** 执行科室上下文（校验必填，用于接口权限或后续扩展过滤）。 */
    private Long executionOrgId;
    /** 诊疗大类，必填；与读模型 category 过滤一致。 */
    private String activityCategoryCode;
}
