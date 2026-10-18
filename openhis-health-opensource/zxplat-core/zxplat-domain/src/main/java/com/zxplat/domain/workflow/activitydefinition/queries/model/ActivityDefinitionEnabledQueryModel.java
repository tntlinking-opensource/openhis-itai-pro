/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.activitydefinition.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 开单场景启用列表查询模型。
 */
@Getter
@Setter
public class ActivityDefinitionEnabledQueryModel {
    /** 主键。 */
    private Long id;
    /** 名称。 */
    private String name;
    /** 类型枚举编码。 */
    private String typeEnum;
    /** 销售/计价单位编码。 */
    private String packageUnitCode;
}
