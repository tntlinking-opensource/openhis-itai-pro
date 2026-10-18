/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.enums;

/**
 * ABO 血型枚举。
 */
public enum BloodTypeAbo implements EnumInterface {
/** A 型（BloodTypeAbo，编码 {@code A}）。 */
    A(0L, "A", "A 型", "", 1L),
/** B 型（BloodTypeAbo，编码 {@code B}）。 */
    B(0L, "B", "B 型", "", 2L),
/** O 型（BloodTypeAbo，编码 {@code O}）。 */
    O(0L, "O", "O 型", "", 3L),
    /** AB 型（BloodTypeAbo，编码 {@code AB}）。 */
    AB(0L, "AB", "AB 型", "", 4L),
    /** 不详（BloodTypeAbo，编码 {@code UNKNOWN}）。 */
    UNKNOWN(0L, "UNKNOWN", "不详", "", 5L),
    /** 未查（BloodTypeAbo，编码 {@code UNTESTED}）。 */
    UNTESTED(0L, "UNTESTED", "未查", "", 9L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * 枚举构造：绑定编码系统、持久化 code、展示名与排序。
     */
    BloodTypeAbo(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
