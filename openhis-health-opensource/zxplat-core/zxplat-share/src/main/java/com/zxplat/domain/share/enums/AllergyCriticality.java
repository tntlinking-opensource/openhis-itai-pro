/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.enums;

import lombok.Getter;

/**
 * 皮试危险程度（DM-CLN-001 §4.1.4 {@code criticality_enum}）。
 */
@Getter
public enum AllergyCriticality implements EnumInterface {

/** 低（AllergyCriticality，持久化编码 {@code low}）。 */
    LOW(0L, "low", "低", "危险程度低", 1L),
/** 高（AllergyCriticality，持久化编码 {@code high}）。 */
    HIGH(0L, "high", "高", "危险程度高", 2L),
/** 无法评估（AllergyCriticality，持久化编码 {@code unassessable}）。 */
    UNASSESSABLE(0L, "unassessable", "无法评估", "暂无法评估危险程度", 3L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    AllergyCriticality(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }
}
