/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.terminology.command.concept;

import lombok.Data;

/**
 * 创建术语概念命令：与 {@link com.zxplat.domain.terminology.service.CodeSystemDomainService#createConcept} 对齐的入参形状。
 */
@Data
public class CreateConceptCommand {
    /** 所属编码系统主键。 */
    private Long codeSystemId;
    /** 概念编码（系统内唯一）。 */
    private String code;
    /** 展示名称。 */
    private String display;
    /** 定义说明。 */
    private String definition;
    /** 父概念主键（可空）。 */
    private Long parentId;
    /** 排序号。 */
    private Long sortOrder;
    /** 是否启用。 */
    private Boolean isActive;
    /** 前端样式类。 */
    private String cssClass;
    /** 列表样式类。 */
    private String listClass;
    /** 是否默认项（Y/N 串，与持久化约定一致）。 */
    private String isDefault;
}
