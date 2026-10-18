/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.domain.reporttemplate.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 检查报告模板读模型。
 */
@Getter
@Setter
public class PacsReportTemplateQueryModel {

    private Long id;

    private String name;

    private String modalityCode;

    private Boolean enabledFlag;

    private Integer sortOrder;

    private Long ownerPractitionerId;
}
