/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.activitydefinition.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 诊疗项目关键字检索查询模型。
 */
@Getter
@Setter
public class ActivityDefinitionSearchQueryModel {
    /** 主键。 */
    private Long id;
    /** 业务号。 */
    private String busNo;
    /** 名称。 */
    private String name;
    /** pystr。 */
    private String pyStr;
    /** wbstr。 */
    private String wbStr;
}
