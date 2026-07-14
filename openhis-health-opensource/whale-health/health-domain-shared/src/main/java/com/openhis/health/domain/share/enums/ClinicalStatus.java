/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.enums;

import lombok.Getter;

/**
 * 皮试临床状态（DM-CLN-001 §4.1.1 {@code clinical_status_enum}）。
 */
@Getter
public enum ClinicalStatus implements EnumInterface {

/** 阳性（ClinicalStatus，持久化编码 {@code active}）。 */
    ACTIVE(0L, "active", "阳性", "当前皮试结果为阳性风险状态", 1L),
/** 阴性（ClinicalStatus，持久化编码 {@code inactive}）。 */
    INACTIVE(0L, "inactive", "阴性", "当前皮试结果为阴性状态", 2L),
/** 已解决（ClinicalStatus，持久化编码 {@code resolved}）。 */
    RESOLVED(0L, "resolved", "已解决", "已处理或不再持续", 3L),
/** 未知（ClinicalStatus，持久化编码 {@code unknown}）。 */
    UNKNOWN(0L, "unknown", "未知", "状态不明确或待确认", 4L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    ClinicalStatus(Long codeSystemId, String code, String display, String definition, Long sortOrder) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }
}
