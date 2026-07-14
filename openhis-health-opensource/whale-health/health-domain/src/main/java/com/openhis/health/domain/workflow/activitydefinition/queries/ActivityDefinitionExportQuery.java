/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.activitydefinition.queries;

import lombok.Getter;
import lombok.Setter;

/**
 * 诊疗目录导出查询入参。
 *
 * <p>默认与列表筛选口径一致；具体导出列由 QueryModel 决定。</p>
 */
@Getter
@Setter
public class ActivityDefinitionExportQuery {
    /** 目录类别编码（可选）。 */
    private String categoryCode;

    /** 关键字（可选）。 */
    private String keyword;

    /** 发布状态（可选）。 */
    private String statusEnum;

    /** 诊疗类型（可选）。 */
    private String typeEnum;
}
