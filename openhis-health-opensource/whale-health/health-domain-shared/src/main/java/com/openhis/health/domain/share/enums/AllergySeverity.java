/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.enums;

import lombok.Getter;

/**
 * 皮试严重程度（DM-CLN-001 §4.1.3 {@code severity_enum}）。
 */
@Getter
public enum AllergySeverity implements EnumInterface {

/** 轻微（AllergySeverity，持久化编码 {@code mild}）。 */
    MILD(0L, "mild", "轻微", "风险或反应程度较轻", 1L),
/** 中度（AllergySeverity，持久化编码 {@code moderate}）。 */
    MODERATE(0L, "moderate", "中度", "风险或反应程度中等", 2L),
/** 严重（AllergySeverity，持久化编码 {@code severe}）。 */
    SEVERE(0L, "severe", "严重", "风险或反应程度较重", 3L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    AllergySeverity(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }
}
