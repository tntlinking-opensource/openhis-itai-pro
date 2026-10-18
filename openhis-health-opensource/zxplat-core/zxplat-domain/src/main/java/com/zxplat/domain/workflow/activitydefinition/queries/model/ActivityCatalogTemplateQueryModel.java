/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.activitydefinition.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * 批量导入模板查询模型。
 */
@Getter
@Setter
public class ActivityCatalogTemplateQueryModel {
    /** 模板名称。 */
    private String templateName;
    /** headers。 */
    private List<String> headers;
    /** samplerows。 */
    private List<Map<String, String>> sampleRows;
}
