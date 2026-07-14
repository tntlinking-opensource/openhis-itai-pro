/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.enums;

/**
 * 在校状态枚举。
 */
public enum StudentStatus implements EnumInterface {
    /** 在校（StudentStatus，编码 {@code IN_SCHOOL}）。 */
    IN_SCHOOL(0L, "IN_SCHOOL", "在校", "", 0L),
    /** 休学（StudentStatus，编码 {@code ON_LEAVE}）。 */
    ON_LEAVE(0L, "ON_LEAVE", "休学", "", 1L),
    /** 离校（StudentStatus，编码 {@code LEFT_SCHOOL}）。 */
    LEFT_SCHOOL(0L, "LEFT_SCHOOL", "离校", "", 2L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /** 枚举构造：绑定编码系统、持久化 code、展示名与排序。 */
    StudentStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
