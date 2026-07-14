/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.enums;

/**
 * 学生体检结果枚举。
 */
public enum PhysicalExamResult implements EnumInterface {
    /** 未体检（PhysicalExamResult，编码 {@code NOT_EXAMINED}）。 */
    NOT_EXAMINED(0L, "NOT_EXAMINED", "未体检", "", 0L),
    /** 体检不合格（PhysicalExamResult，编码 {@code FAILED}）。 */
    FAILED(0L, "FAILED", "体检不合格", "", 1L),
    /** 体检合格（PhysicalExamResult，编码 {@code PASSED}）。 */
    PASSED(0L, "PASSED", "体检合格", "", 2L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * 枚举构造：绑定编码系统、持久化 code、展示名与排序。
     */
    PhysicalExamResult(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
