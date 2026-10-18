/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.activitydefinition.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 诊疗目录医保对码状态查询模型。
 */
@Getter
@Setter
public class ActivityYbMappingStatusQueryModel {
    /** 活动主键。 */
    private Long activityId;
    /** 医保匹配标志。 */
    private Boolean ybMatchFlag;
    /** 医保号。 */
    private String ybNo;
    /** chrgitmlv。 */
    private Integer chrgitmLv;
}
