/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.enums;

/**
 * 位置状态枚举。
 */
public enum LocationStatus implements EnumInterface {
    /** 草稿（LocationStatus，编码 {@code DRAFT}）。 */
    DRAFT(0L, "DRAFT", "草稿", "", 0L),
    /** 启用（LocationStatus，编码 {@code ACTIVE}）。 */
    ACTIVE(0L, "ACTIVE", "启用", "", 1L),
    /** 停用（LocationStatus，编码 {@code INACTIVE}）。 */
    INACTIVE(0L, "INACTIVE", "停用", "", 2L),
    /** 暂停（LocationStatus，编码 {@code SUSPENDED}）。 */
    SUSPENDED(0L, "SUSPENDED", "暂停", "", 3L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /** 枚举构造：绑定编码系统、持久化 code、展示名与排序。 */
    LocationStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }

    public Long getCodeSystemId() {
        return codeSystemId;
    }

    public String getCode() {
        return code;
    }

    public String getDisplay() {
        return display;
    }

    public String getDefinition() {
        return definition;
    }

    public Long getSortOrder() {
        return sortOrder;
    }
}
