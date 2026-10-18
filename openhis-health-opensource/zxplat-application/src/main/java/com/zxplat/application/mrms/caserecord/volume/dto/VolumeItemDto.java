/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.mrms.caserecord.volume.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 卷宗目录项 DTO。
 */
@Getter
@Setter
public class VolumeItemDto {

    /** 条目类型（homepage/emr_document）。 */
    private String itemType;

    /** 关联实体主键（病案或 EMR 文档）。 */
    private Long refId;

    /** 展示标题。 */
    private String title;

    /** EMR 文档类型编码。 */
    private String documentTypeCode;

    /** EMR 文档类型展示名。 */
    private String documentTypeName;

    /** 文档创建时间。 */
    private LocalDateTime createdAt;

    /** 是否可打印/合并进卷宗 PDF。 */
    private boolean printable;
}
