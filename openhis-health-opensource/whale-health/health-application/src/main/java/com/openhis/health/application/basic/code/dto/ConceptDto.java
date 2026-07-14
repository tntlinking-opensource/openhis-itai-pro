/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.code.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 编码概念（术语条目）DTO：隶属于某一 {@link CodeSystemDto}。
 *
 */
@Data
public class ConceptDto {

    /** 概念主键。 */
    private Long id;
    /** 所属编码体系 ID。 */
    private Long codeSystemId;
    /** 编码体系名称（冗余展示）。 */
    private String codeSystemName;
    /** 概念编码（业务键）。 */
    private String code;
    /** 显示名称。 */
    private String display;
    /** 定义说明。 */
    private String definition;
    /** 父概念 ID（树形码表）。 */
    private Long parentId;
    /** 排序序号。 */
    private Long sortOrder;
    /** 是否启用。 */
    private Boolean isActive;
    /** 前端样式类（可选）。 */
    private String cssClass;
    /** 列表样式类（可选）。 */
    private String listClass;
    /** 是否默认项标记。 */
    private String isDefault;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;

    /** 子概念列表（树加载时使用）。 */
    private List<ConceptDto> children;

}
