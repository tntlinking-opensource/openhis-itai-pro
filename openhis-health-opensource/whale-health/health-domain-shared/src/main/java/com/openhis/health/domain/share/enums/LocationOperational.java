/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.enums;

/**
 * 位置运营状态枚举。
 */
public enum LocationOperational implements EnumInterface {
    /** 使用中（LocationOperational，编码 {@code IN_USE}）。 */
    IN_USE(0L, "IN_USE", "使用中", "", 1L),
    /** 未使用（LocationOperational，编码 {@code NOT_IN_USE}）。 */
    NOT_IN_USE(0L, "NOT_IN_USE", "未使用", "", 2L),
    /** 预留：床位预约预留（LocationOperational，编码 {@code RESERVED}）。 */
    RESERVED(0L, "RESERVED", "预留", "", 3L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /** 枚举构造：绑定编码系统、持久化 code、展示名与排序。 */
    LocationOperational(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
