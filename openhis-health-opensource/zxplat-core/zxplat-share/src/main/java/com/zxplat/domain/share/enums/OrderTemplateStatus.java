/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.enums;

/**
 * 组套模板状态枚举。
 */
public enum OrderTemplateStatus implements EnumInterface {
    /** 草稿（OrderTemplateStatus，编码 {@code DRAFT}）。 */
    DRAFT(0L, "DRAFT", "草稿", "", 1L),
    /** 已启用（OrderTemplateStatus，编码 {@code ACTIVE}）。 */
    ACTIVE(0L, "ACTIVE", "已启用", "", 2L),
    /** 已停用（OrderTemplateStatus，编码 {@code RETIRED}）。 */
    RETIRED(0L, "RETIRED", "已停用", "", 3L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * 枚举构造：绑定编码系统、持久化 code、展示名与排序。
     */
    OrderTemplateStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
