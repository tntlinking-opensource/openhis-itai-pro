/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.application.documentType.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateDocumentTypeRequest {

    /** 业务编码（唯一） */
    private String code;
    /** 名称 */
    private String name;
    /** 默认模板 id */
    private Long defaultTemplateId;
    /** 是否启用 */
    private Boolean isEnabled = true;
    /** 排序序号 */
    private Integer sortOrder;
    /** 文书分类：OUTPATIENT / INPATIENT / NOTICE / SURGERY / OTHER */
    private String categoryCode;
}
