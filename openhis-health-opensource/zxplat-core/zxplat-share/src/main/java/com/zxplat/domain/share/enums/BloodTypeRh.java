/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.enums;

/**
 * Rh 血型枚举。
 */
public enum BloodTypeRh implements EnumInterface {
    /** Rh 阴性（BloodTypeRh，编码 {@code NEGATIVE}）。 */
    NEGATIVE(0L, "NEGATIVE", "Rh 阴性", "", 1L),
    /** Rh 阳性（BloodTypeRh，编码 {@code POSITIVE}）。 */
    POSITIVE(0L, "POSITIVE", "Rh 阳性", "", 2L),
    /** 不详（BloodTypeRh，编码 {@code UNKNOWN}）。 */
    UNKNOWN(0L, "UNKNOWN", "不详", "", 3L),
    /** 未查（BloodTypeRh，编码 {@code UNTESTED}）。 */
    UNTESTED(0L, "UNTESTED", "未查", "", 4L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /**
     * 枚举构造：绑定编码系统、持久化 code、展示名与排序。
     */
    BloodTypeRh(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
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
