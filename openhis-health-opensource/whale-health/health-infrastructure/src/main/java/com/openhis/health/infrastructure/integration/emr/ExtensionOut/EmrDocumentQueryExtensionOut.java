/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.emr.ExtensionOut;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * EMR 文书摘要（卷宗目录 / 质控读侧）。
 *
 * <p>职责：供住院质控、病案等模块只读展示 EMR 文书元数据，不含正文内容。</p>
 */
@Getter
@Setter
public class EmrDocumentQueryExtensionOut {

    /** 文书主键。 */
    private Long documentId;

    /** 文书标题。 */
    private String title;

    /** 文书类型字典 code。 */
    private String documentTypeCode;

    /** 文书类型展示名。 */
    private String documentTypeName;

    /** 文书状态 code（草稿/已提交等）。 */
    private String statusCode;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 是否允许打印/导出 PDF。 */
    private boolean printable;
}
