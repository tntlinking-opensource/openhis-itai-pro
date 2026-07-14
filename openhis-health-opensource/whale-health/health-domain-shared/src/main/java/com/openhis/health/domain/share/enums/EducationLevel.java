/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.enums;

/**
 * 学历层次枚举。
 */
public enum EducationLevel implements EnumInterface {
    /** 预科生（EducationLevel，编码 {@code PREPARATORY}）。 */
    PREPARATORY(0L, "PREPARATORY", "预科生", "", 0L),
    /** 本科生（EducationLevel，编码 {@code UNDERGRADUATE}）。 */
    UNDERGRADUATE(0L, "UNDERGRADUATE", "本科生", "", 1L),
    /** 硕士研究生（EducationLevel，编码 {@code POSTGRADUATE}）。 */
    POSTGRADUATE(0L, "POSTGRADUATE", "硕士研究生", "", 2L),
    /** 博士研究生（EducationLevel，编码 {@code DOCTORAL}）。 */
    DOCTORAL(0L, "DOCTORAL", "博士研究生", "", 3L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * 枚举构造：绑定编码系统、持久化 code、展示名与排序。
     */
    EducationLevel(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
