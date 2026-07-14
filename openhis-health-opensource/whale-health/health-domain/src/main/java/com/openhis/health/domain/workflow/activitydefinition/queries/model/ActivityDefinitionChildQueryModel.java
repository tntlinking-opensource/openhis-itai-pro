/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.activitydefinition.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 诊疗项目子项查询模型。
 */
@Getter
@Setter
public class ActivityDefinitionChildQueryModel {
    /** 主键。 */
    private Long id;
    /** 活动主键。 */
    private Long activityId;
    /** children主键。 */
    private Long childrenId;
    /** children号。 */
    private Integer childrenNum;
    /** children名称。 */
    private String childrenName;
}
