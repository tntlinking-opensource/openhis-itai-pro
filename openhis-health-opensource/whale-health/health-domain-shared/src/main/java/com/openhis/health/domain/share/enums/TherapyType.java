/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.enums;

import lombok.Getter;

/**
 * 治疗类型枚举。
 */
@Getter
public enum TherapyType implements EnumInterface {
    /** 长期医嘱（TherapyType，编码 {@code LONG_TERM}）。 */
    LONG_TERM(0L, "LONG_TERM", "长期医嘱", "", 1L),
    /** 临时医嘱（TherapyType，编码 {@code TEMPORARY}）。 */
    TEMPORARY(0L, "TEMPORARY", "临时医嘱", "", 2L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    /** 枚举构造：绑定编码系统、持久化 code、展示名与排序。 */
    TherapyType(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }

}
