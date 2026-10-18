/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.conditiondefinition.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 诊断定义详情模型。
 */
@Getter
@Setter
public class ConditionDefinitionDetailQueryModel {
    /** 主键。 */
    private Long id;
    /** 病情编码。 */
    private String conditionCode;
    /** 名称。 */
    private String name;
    /** source枚举编码。 */
    private String sourceEnum;
    /** 状态枚举编码。 */
    private String statusEnum;
}
