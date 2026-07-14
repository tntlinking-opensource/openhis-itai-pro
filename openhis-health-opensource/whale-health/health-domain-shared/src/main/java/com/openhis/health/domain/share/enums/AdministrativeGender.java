/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.enums;

/**
 * 行政性别枚举。
 */
public enum AdministrativeGender implements EnumInterface {
    /** 男性（AdministrativeGender，编码 {@code MALE}）。 */
    MALE(0L, "MALE", "男性", "", 0L),
    /** 女性（AdministrativeGender，编码 {@code FEMALE}）。 */
    FEMALE(0L, "FEMALE", "女性", "", 1L),
    /** 未知（AdministrativeGender，编码 {@code UNKNOWN}）。 */
    UNKNOWN(0L, "UNKNOWN", "未知", "", 2L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * 枚举构造：绑定编码系统、持久化 code、展示名与排序。
     */
    AdministrativeGender(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
