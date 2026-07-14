/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.emr.application.documentType.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class DocumentTypeDto {

    /** 主键 id */
    private Long id;
    /** 业务编码（唯一） */
    private String code;
    /** 名称 */
    private String name;
    /** 默认模板 id */
    private Long defaultTemplateId;
    /** 默认模板名称 */
    private String defaultTemplateName;
    /** 是否启用 */
    private Boolean isEnabled;
    /** 排序序号 */
    private Integer sortOrder;
    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 最后更新时间 */
    private LocalDateTime updatedAt;
}
