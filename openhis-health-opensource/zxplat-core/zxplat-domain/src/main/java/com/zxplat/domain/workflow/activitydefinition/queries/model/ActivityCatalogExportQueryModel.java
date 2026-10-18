/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.activitydefinition.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 诊疗目录导出查询模型。
 */
@Getter
@Setter
/**
 * ActivityCatalogExportQueryModel。
 */
public class ActivityCatalogExportQueryModel {
    /** rows。 */
    /**
     * rows。
     */
    private List<ActivityCatalogExportRowQueryModel> rows;
    /** total。 */
    /**
     * total。
     */
    private Long total;
}
