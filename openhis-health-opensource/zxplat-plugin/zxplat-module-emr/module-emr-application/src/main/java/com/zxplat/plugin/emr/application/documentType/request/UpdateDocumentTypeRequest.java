/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.application.documentType.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateDocumentTypeRequest {

    /** 主键 id */
    private Long id;
    /** 名称 */
    private String name;
    /** 默认模板 id */
    private Long defaultTemplateId;
    /** 为 true 时清空默认模板，优先级高于 defaultTemplateId */
    private Boolean clearDefaultTemplate;
    /** 是否启用 */
    private Boolean isEnabled;
    /** 排序序号 */
    private Integer sortOrder;
    /** 文书分类：OUTPATIENT / INPATIENT / NOTICE / SURGERY / OTHER */
    private String categoryCode;
}
