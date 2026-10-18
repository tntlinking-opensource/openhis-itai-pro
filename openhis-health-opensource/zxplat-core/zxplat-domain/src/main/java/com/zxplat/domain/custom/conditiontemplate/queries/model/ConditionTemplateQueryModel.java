/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.conditiontemplate.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 诊断模板查询模型。
 */
@Getter
@Setter
public class ConditionTemplateQueryModel {
    /** 主键。 */
    private Long id;
    /** object主键。 */
    private Long objectId;
    /** 目录主键。 */
    private Long definitionId;
    /** binding枚举编码。 */
    private String bindingEnum;
    /** sortweight。 */
    private Integer sortWeight;
    /** remark。 */
    private String remark;
}
