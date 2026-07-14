/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.activitydefinition.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 诊疗项目目录分页列表行查询模型。
 */
@Getter
@Setter
public class ActivityDefinitionPageQueryModel {
    /** 主键。 */
    private Long id;
    /** 业务号。 */
    private String busNo;
    /** 名称。 */
    private String name;
    /** 类别编码。 */
    private String categoryCode;
    /** 销售/计价单位编码。 */
    private String packageUnitCode;
    /** 默认就诊单位编码。 */
    private String defEncounterUnitCode;
    /** 类型枚举编码。 */
    private String typeEnum;
    /** 状态枚举编码。 */
    private String statusEnum;
    /** 医保标志。 */
    private Boolean ybFlag;
    /** children标志。 */
    private Boolean childrenFlag;
}
