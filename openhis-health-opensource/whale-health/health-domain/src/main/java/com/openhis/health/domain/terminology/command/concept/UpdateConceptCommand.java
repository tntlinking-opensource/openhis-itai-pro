/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.terminology.command.concept;

import lombok.Data;

/**
 * 更新术语概念命令：与 {@link com.openhis.health.domain.terminology.service.CodeSystemDomainService#updateConcept} 对齐的入参形状。
 */
@Data
public class UpdateConceptCommand {
    /** 概念主键。 */
    private Long id;
    /** 新概念编码（变更时重校唯一性）。 */
    private String code;
    /** 展示名称。 */
    private String display;
    /** 定义说明。 */
    private String definition;
    /** 父概念主键。 */
    private Long parentId;
    /** 排序号。 */
    private Long sortOrder;
    /** 是否启用。 */
    private Boolean isActive;
    /** 前端样式类。 */
    private String cssClass;
    /** 列表样式类。 */
    private String listClass;
    /** 是否默认项（Y/N）。 */
    private String isDefault;
}
