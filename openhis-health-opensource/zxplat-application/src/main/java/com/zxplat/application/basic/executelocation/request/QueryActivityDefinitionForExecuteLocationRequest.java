/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.executelocation.request;

import com.zxplat.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 为执行地点配置「挑选诊疗定义」提供的分页查询：走产品定义读模型，限定活动定义表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class QueryActivityDefinitionForExecuteLocationRequest extends AbstractHealthPageRequest {

    /** 开立科室上下文（校验必填，与配置页左侧选中科室一致）。 */
    private Long organizationId;
    /** 诊疗大类，必填；与读模型 category 过滤一致。 */
    private String activityCategoryCode;
    /** 业务类型码，可选；有值时下推目录 {@code typeEnum} 过滤。 */
    private String activityTypeCode;
}
