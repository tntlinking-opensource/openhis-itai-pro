/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.enums;

/**
 * 患者非身份证标识状态枚举。
 */
public enum IdentifierState implements EnumInterface {
    /** 常用（IdentifierState，编码 {@code USUAL}）。 */
    USUAL(0L, "USUAL", "常用", "", 1L),
    /** 官方（IdentifierState，编码 {@code OFFICIAL}）。 */
    OFFICIAL(0L, "OFFICIAL", "官方", "", 2L),
    /** 临时（IdentifierState，编码 {@code TEMP}）。 */
    TEMP(0L, "TEMP", "临时", "", 3L),
    /** 次要（IdentifierState，编码 {@code SECONDARY}）。 */
    SECONDARY(0L, "SECONDARY", "次要", "", 4L),
    /** 已失效（IdentifierState，编码 {@code OLD}）。 */
    OLD(0L, "OLD", "已失效", "", 5L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /** 枚举构造：绑定编码系统、持久化 code、展示名与排序。 */
    IdentifierState(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
