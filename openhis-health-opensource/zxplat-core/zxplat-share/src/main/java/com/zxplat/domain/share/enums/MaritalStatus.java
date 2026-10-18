/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.enums;

/**
 * 婚姻状态枚举。
 */
public enum MaritalStatus implements EnumInterface {
    /** 未婚（MaritalStatus，编码 {@code UNMARRIED}）。 */
    UNMARRIED(0L, "UNMARRIED", "未婚", "", 10L),
    /** 已婚（MaritalStatus，编码 {@code MARRIED}）。 */
    MARRIED(0L, "MARRIED", "已婚", "", 20L),
    /** 初婚（MaritalStatus，编码 {@code FIRST_MARRIAGE}）。 */
    FIRST_MARRIAGE(0L, "FIRST_MARRIAGE", "初婚", "", 21L),
    /** 再婚（MaritalStatus，编码 {@code REMARRIAGE}）。 */
    REMARRIAGE(0L, "REMARRIAGE", "再婚", "", 22L),
    /** 复婚（MaritalStatus，编码 {@code RESTORED_MARRIAGE}）。 */
    RESTORED_MARRIAGE(0L, "RESTORED_MARRIAGE", "复婚", "", 23L),
    /** 丧偶（MaritalStatus，编码 {@code WIDOWED}）。 */
    WIDOWED(0L, "WIDOWED", "丧偶", "", 30L),
    /** 离婚（MaritalStatus，编码 {@code DIVORCED}）。 */
    DIVORCED(0L, "DIVORCED", "离婚", "", 40L),
    /** 未说明（MaritalStatus，编码 {@code UNSPECIFIED}）。 */
    UNSPECIFIED(0L, "UNSPECIFIED", "未说明", "", 90L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * 枚举构造：绑定编码系统、持久化 code、展示名与排序。
     */
    MaritalStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
