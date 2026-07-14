/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.enums;

import lombok.Getter;

/**
 * 画布打印模板状态（DM-PRT-001 §4.1 · {@code prt_template.status}）。
 */
@Getter
public enum PrintTemplateStatus implements EnumInterface {

    /** 设计态草稿，可 saveDraft。 */
    DRAFT(0L, "draft", "草稿", "", 1L),

    /** 已发布，运行时 preparePrint 可读。 */
    PUBLISHED(0L, "published", "已发布", "", 2L),

    /** 历史 published 归档，不参与运行时解析。 */
    ARCHIVED(0L, "archived", "已归档", "", 3L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    PrintTemplateStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }

    /**
     * 判断状态码是否与当前枚举值匹配。
     *
     * @param statusCode 库表 {@code status} 列值
     * @return 匹配返回 {@code true}
     */
    public boolean sameAs(String statusCode) {
        return this.code.equals(statusCode);
    }
}
